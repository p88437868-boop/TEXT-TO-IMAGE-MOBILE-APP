import { displayStatus } from "./db.ts";
import { daysLeft, HttpError, planLabel, quotaInfo, type Settings } from "./licenses.ts";
import type { EventRow, LicenseRow, QuotaMode } from "./types.ts";
import { dayKey, normalizeDeviceCode } from "./util.ts";

const DAY_MS = 86_400_000;

export interface ApiLicense {
  id: string;
  key: string;
  name: string;
  note: string | null;
  status: string;
  deviceCode: string;
  durationDays: number | null;
  createdAt: number;
  activatedAt: number | null;
  expiresAt: number | null;
  deviceModel: string | null;
  androidVersion: string | null;
  appVersion: string | null;
  lastSeenAt: number | null;
  plan: string;
  quotaMode: QuotaMode;
  quotaLimit: number | null;
  usedToday: number;
  usedTotal: number;
  remaining: number | null;
  dayEndsAt: number;
  daysLeft: number | null;
}

export function toApi(row: LicenseRow, now: number, timeZone: string): ApiLicense {
  const quota = quotaInfo(row, now, timeZone);
  return {
    id: row.id,
    key: row.license_key,
    name: row.name,
    note: row.note,
    status: displayStatus(row, now),
    deviceCode: row.device_code,
    durationDays: row.duration_days,
    createdAt: row.created_at,
    activatedAt: row.activated_at,
    expiresAt: row.expires_at,
    deviceModel: row.device_model,
    androidVersion: row.android_version,
    appVersion: row.app_version,
    lastSeenAt: row.last_seen_at,
    plan: planLabel(row),
    quotaMode: row.quota_mode,
    quotaLimit: row.quota_limit,
    usedToday: quota.usedToday,
    usedTotal: quota.usedTotal,
    remaining: quota.remaining,
    dayEndsAt: quota.dayEndsAt,
    daysLeft: daysLeft(row, now),
  };
}

function toApiEvent(event: EventRow): Record<string, unknown> {
  return { ts: event.ts, type: event.type, detail: event.detail };
}

async function requireLicense(settings: Settings, id: string): Promise<LicenseRow> {
  const row = await settings.db.getById(id);
  if (!row) throw new HttpError(404, "NOT_FOUND", "No license with that id.");
  return row;
}

export async function listLicenses(
  settings: Settings,
  search: string,
  status: string,
): Promise<ApiLicense[]> {
  const rows = await settings.db.list({ search });
  const wanted = (status ?? "").toLowerCase();
  const tz = settings.quotaTz;
  return rows
    .map((row) => toApi(row, settings.now, tz))
    .filter((row) => !wanted || wanted === "all" || row.status === wanted);
}

export async function licenseDetail(
  settings: Settings,
  id: string,
): Promise<{ license: ApiLicense; events: Record<string, unknown>[] }> {
  const row = await requireLicense(settings, id);
  const events = await settings.db.events(id, 100);
  return {
    license: toApi(row, settings.now, settings.quotaTz),
    events: events.map(toApiEvent),
  };
}

export async function setBlocked(
  settings: Settings,
  id: string,
  blocked: boolean,
): Promise<ApiLicense> {
  const row = await requireLicense(settings, id);
  if (row.status === "cancelled") {
    throw new HttpError(409, "CANCELLED", "This license was cancelled and cannot change status.");
  }
  const status = blocked ? "blocked" : "active";
  await settings.db.update(id, { status });
  await settings.db.addEvent(id, settings.now, blocked ? "blocked" : "unblocked", null);
  return toApi({ ...row, status }, settings.now, settings.quotaTz);
}

export async function cancelLicense(settings: Settings, id: string): Promise<ApiLicense> {
  const row = await requireLicense(settings, id);
  if (row.status === "cancelled") {
    throw new HttpError(409, "ALREADY_CANCELLED", "This license is already cancelled.");
  }
  await settings.db.update(id, { status: "cancelled" });
  await settings.db.addEvent(id, settings.now, "cancelled", null);
  return toApi({ ...row, status: "cancelled" }, settings.now, settings.quotaTz);
}

export async function movePhone(
  settings: Settings,
  id: string,
  deviceCodeInput: unknown,
): Promise<ApiLicense> {
  const row = await requireLicense(settings, id);
  const deviceCode = normalizeDeviceCode(deviceCodeInput);
  if (!deviceCode) {
    throw new HttpError(400, "INVALID_DEVICE_CODE", "That is not a valid Device Code.");
  }
  if (deviceCode === row.device_code) {
    throw new HttpError(400, "SAME_DEVICE", "That is already the phone on this license.");
  }
  const other = await settings.db.getLiveByDevice(deviceCode);
  if (other && other.id !== id) {
    throw new HttpError(
      409,
      "DEVICE_ALREADY_LICENSED",
      `That phone already has a license (${other.name}).`,
    );
  }
  await settings.db.update(id, { device_code: deviceCode });
  await settings.db.addEvent(
    id,
    settings.now,
    "moved_phone",
    `${row.device_code} -> ${deviceCode}`,
  );
  return toApi({ ...row, device_code: deviceCode }, settings.now, settings.quotaTz);
}

export async function extendLicense(
  settings: Settings,
  id: string,
  body: { days?: unknown; date?: unknown; lifetime?: unknown },
): Promise<ApiLicense> {
  const row = await requireLicense(settings, id);
  const patch: Partial<LicenseRow> = {};

  if (body.lifetime === true) {
    patch.expires_at = null;
    patch.duration_days = null;
  } else if (typeof body.date === "number" && Number.isFinite(body.date)) {
    if (body.date <= settings.now) {
      throw new HttpError(400, "INVALID_DATE", "That date is in the past.");
    }
    patch.expires_at = Math.trunc(body.date);
  } else {
    const days = Number(body.days);
    if (!Number.isInteger(days) || days < 1 || days > 36_500) {
      throw new HttpError(400, "INVALID_DURATION", "Days must be between 1 and 36500.");
    }
    const base = row.expires_at !== null && row.expires_at > settings.now ? row.expires_at : settings.now;
    patch.expires_at = base + days * DAY_MS;
  }

  await settings.db.update(id, patch);
  await settings.db.addEvent(
    id,
    settings.now,
    "extended",
    patch.expires_at === null ? "lifetime" : `until ${new Date(patch.expires_at).toISOString()}`,
  );
  return toApi({ ...row, ...patch }, settings.now, settings.quotaTz);
}

export async function patchLicense(
  settings: Settings,
  id: string,
  body: { name?: unknown; note?: unknown },
): Promise<ApiLicense> {
  const row = await requireLicense(settings, id);
  const patch: Partial<LicenseRow> = {};
  let changedName = false;
  if (typeof body.name === "string") {
    const name = body.name.trim();
    if (!name) throw new HttpError(400, "NAME_REQUIRED", "Name cannot be empty.");
    if (name.length > 64) throw new HttpError(400, "NAME_TOO_LONG", "Name is limited to 64 characters.");
    patch.name = name;
    changedName = name !== row.name;
  }
  if (typeof body.note === "string") patch.note = body.note.trim() || null;
  if (Object.keys(patch).length === 0) {
    throw new HttpError(400, "NOTHING_TO_CHANGE", "Send a name or a note.");
  }
  await settings.db.update(id, patch);
  if (changedName) {
    await settings.db.addEvent(id, settings.now, "name_changed", `${row.name} -> ${patch.name}`);
  }
  return toApi({ ...row, ...patch }, settings.now, settings.quotaTz);
}

export async function deleteLicense(settings: Settings, id: string): Promise<void> {
  const row = await requireLicense(settings, id);
  if (row.status !== "cancelled") {
    throw new HttpError(409, "NOT_CANCELLED", "Only cancelled licenses can be deleted.");
  }
  await settings.db.remove(id);
}

export async function setQuota(
  settings: Settings,
  id: string,
  modeInput: unknown,
  limitInput: unknown,
): Promise<ApiLicense> {
  const row = await requireLicense(settings, id);
  if (modeInput !== "unlimited" && modeInput !== "daily" && modeInput !== "total") {
    throw new HttpError(400, "INVALID_QUOTA_MODE", "Mode must be unlimited, daily or total.");
  }
  const mode: QuotaMode = modeInput;
  let limit: number | null = null;
  if (mode !== "unlimited") {
    const value = Number(limitInput);
    if (!Number.isInteger(value) || value < 1 || value > 1_000_000) {
      throw new HttpError(400, "INVALID_QUOTA", "The image limit must be between 1 and 1000000.");
    }
    limit = value;
  }
  await settings.db.update(id, { quota_mode: mode, quota_limit: limit });
  await settings.db.addEvent(
    id,
    settings.now,
    "quota_changed",
    mode === "unlimited" ? "unlimited" : `${mode} ${limit}`,
  );
  return toApi({ ...row, quota_mode: mode, quota_limit: limit }, settings.now, settings.quotaTz);
}

export async function addUsage(
  settings: Settings,
  id: string,
  countInput: unknown,
): Promise<ApiLicense> {
  const row = await requireLicense(settings, id);
  const count = Number(countInput);
  if (!Number.isInteger(count) || Math.abs(count) > 100_000) {
    throw new HttpError(400, "INVALID_COUNT", "Count must be a whole number.");
  }
  const today = dayKey(settings.now, settings.quotaTz);
  const usedTotal = Math.max(0, row.used_total + count);
  const usedToday = Math.max(0, (row.used_day === today ? row.used_today : 0) + count);
  await settings.db.update(id, { used_total: usedTotal, used_today: usedToday, used_day: today });
  await settings.db.addEvent(id, settings.now, "usage_reset", `add ${count} images`);
  const updated = { ...row, used_total: usedTotal, used_today: usedToday, used_day: today };
  return toApi(updated, settings.now, settings.quotaTz);
}

export async function resetUsage(
  settings: Settings,
  id: string,
  scope: unknown,
): Promise<ApiLicense> {
  const row = await requireLicense(settings, id);
  if (scope !== "today" && scope !== "total") {
    throw new HttpError(400, "INVALID_SCOPE", "Scope must be today or total.");
  }
  const today = dayKey(settings.now, settings.quotaTz);
  const patch: Partial<LicenseRow> =
    scope === "today"
      ? { used_today: 0, used_day: today }
      : { used_today: 0, used_day: today, used_total: 0 };
  await settings.db.update(id, patch);
  await settings.db.addEvent(id, settings.now, "usage_reset", scope);
  return toApi({ ...row, ...patch }, settings.now, settings.quotaTz);
}

export async function stats(settings: Settings): Promise<Record<string, number>> {
  const rows = await settings.db.list({});
  const now = settings.now;
  const counts = { total: 0, active: 0, pending: 0, expired: 0, blocked: 0, cancelled: 0 };
  let expiringSoon = 0;
  let seenRecently = 0;
  for (const row of rows) {
    counts.total++;
    const status = displayStatus(row, now);
    if (status in counts) counts[status as keyof typeof counts]++;
    if (row.status === "active" && row.expires_at !== null) {
      if (row.expires_at > now && row.expires_at <= now + 7 * DAY_MS) expiringSoon++;
    }
    if (row.last_seen_at !== null && row.last_seen_at >= now - 48 * 3_600_000) seenRecently++;
  }
  return { ...counts, expiringSoon, seenRecently };
}

function csvCell(value: unknown): string {
  const text = value === null || value === undefined ? "" : String(value);
  return /[",\n]/.test(text) ? `"${text.replace(/"/g, '""')}"` : text;
}

export async function exportCsv(settings: Settings): Promise<string> {
  const rows = await settings.db.list({});
  const tz = settings.quotaTz;
  const header = [
    "id",
    "name",
    "key",
    "status",
    "plan",
    "device_code",
    "device_model",
    "android_version",
    "app_version",
    "duration_days",
    "created_at",
    "activated_at",
    "expires_at",
    "last_seen_at",
    "used_today",
    "used_total",
    "note",
  ];
  const lines = [header.join(",")];
  for (const row of rows) {
    const api = toApi(row, settings.now, tz);
    const iso = (ts: number | null) => (ts === null ? "" : new Date(ts).toISOString());
    lines.push(
      [
        api.id,
        api.name,
        api.key,
        api.status,
        api.plan,
        api.deviceCode,
        api.deviceModel,
        api.androidVersion,
        api.appVersion,
        api.durationDays,
        iso(api.createdAt),
        iso(api.activatedAt),
        iso(api.expiresAt),
        iso(api.lastSeenAt),
        api.usedToday,
        api.usedTotal,
        api.note,
      ]
        .map(csvCell)
        .join(","),
    );
  }
  return lines.join("\n") + "\n";
}

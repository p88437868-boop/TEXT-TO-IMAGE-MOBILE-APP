import type { Db } from "./db.ts";
import type {
  LicenseRow,
  QuotaInfo,
  QuotaMode,
  VerifyPayload,
  VerifyRequest,
} from "./types.ts";
import {
  dayKey,
  generateLicenseKey,
  nextMidnight,
  normalizeDeviceCode,
  normalizeLicenseKey,
  randomId,
} from "./util.ts";

export interface Settings {
  db: Db;
  now: number;
  quotaTz: string;
}

export class HttpError extends Error {
  readonly status: number;
  readonly code: string;

  constructor(status: number, code: string, message: string) {
    super(message);
    this.status = status;
    this.code = code;
  }
}

const DAY_MS = 86_400_000;

export function quotaInfo(row: LicenseRow, now: number, timeZone: string): QuotaInfo {
  const today = dayKey(now, timeZone);
  const rolledOver = row.used_day !== today;
  const usedToday = rolledOver ? 0 : row.used_today;
  const usedTotal = row.used_total;
  const limit = row.quota_mode === "unlimited" ? null : row.quota_limit;
  const used =
    row.quota_mode === "daily" ? usedToday : row.quota_mode === "total" ? usedTotal : null;
  return {
    mode: row.quota_mode,
    limit,
    usedToday,
    usedTotal,
    remaining: limit === null || used === null ? null : Math.max(0, limit - used),
    dayEndsAt: nextMidnight(now, timeZone),
  };
}

export interface CreateInput {
  name: string;
  deviceCode: string;
  durationDays: number | null;
  note: string | null;
  quotaMode: QuotaMode;
  quotaLimit: number | null;
}

export async function createLicense(
  settings: Settings,
  input: CreateInput,
): Promise<LicenseRow> {
  const name = input.name.trim();
  if (!name) throw new HttpError(400, "NAME_REQUIRED", "A customer name is required.");
  if (name.length > 64) throw new HttpError(400, "NAME_TOO_LONG", "Name is limited to 64 characters.");

  const deviceCode = normalizeDeviceCode(input.deviceCode);
  if (!deviceCode) {
    throw new HttpError(
      400,
      "INVALID_DEVICE_CODE",
      "That is not a valid Device Code. Expected TP-XXXX-XXXX-XXXX-XXXX.",
    );
  }

  if (input.durationDays !== null) {
    if (!Number.isInteger(input.durationDays) || input.durationDays < 1 || input.durationDays > 36_500) {
      throw new HttpError(400, "INVALID_DURATION", "Duration must be between 1 and 36500 days.");
    }
  }

  let quotaLimit: number | null = null;
  if (input.quotaMode !== "unlimited") {
    if (
      input.quotaLimit === null ||
      !Number.isInteger(input.quotaLimit) ||
      input.quotaLimit < 1 ||
      input.quotaLimit > 1_000_000
    ) {
      throw new HttpError(400, "INVALID_QUOTA", "The image limit must be between 1 and 1000000.");
    }
    quotaLimit = input.quotaLimit;
  }

  const existing = await settings.db.getLiveByDevice(deviceCode);
  if (existing) {
    throw new HttpError(
      409,
      "DEVICE_ALREADY_LICENSED",
      `This phone already has a license (${existing.name}, ${existing.license_key}). Extend that one instead.`,
    );
  }

  let licenseKey = generateLicenseKey();
  for (let attempt = 0; attempt < 5; attempt++) {
    const clash = await settings.db.getByKey(licenseKey);
    if (!clash) break;
    licenseKey = generateLicenseKey();
  }

  const row: LicenseRow = {
    id: randomId(),
    license_key: licenseKey,
    name,
    note: input.note?.trim() || null,
    status: "active",
    device_code: deviceCode,
    duration_days: input.durationDays,
    created_at: settings.now,
    activated_at: null,
    expires_at: null,
    device_model: null,
    android_version: null,
    app_version: null,
    last_seen_at: null,
    quota_mode: input.quotaMode,
    quota_limit: quotaLimit,
    used_total: 0,
    used_today: 0,
    used_day: null,
  };
  await settings.db.insert(row);
  await settings.db.addEvent(row.id, settings.now, "created", `key ${licenseKey}`);
  return row;
}

function emptyPayload(
  status: VerifyPayload["status"],
  now: number,
  nonce: string,
  reportedId: string | null = null,
): VerifyPayload {
  return {
    status,
    name: null,
    expiresAt: null,
    activatedAt: null,
    serverTime: now,
    nonce,
    quota: null,
    reportedId,
  };
}

export interface VerifyOutcome {
  payload: VerifyPayload;
  /** True when the attempt counts towards the rate limit. */
  failedAttempt: boolean;
}

export async function verifyLicense(
  settings: Settings,
  request: VerifyRequest,
): Promise<VerifyOutcome> {
  const now = settings.now;
  const nonce = request.nonce ?? "";
  const deviceCode = normalizeDeviceCode(request.deviceCode);
  const licenseKey = normalizeLicenseKey(request.key);

  if (!deviceCode) {
    return { payload: emptyPayload("WRONG_DEVICE", now, nonce), failedAttempt: true };
  }
  if (!licenseKey) {
    return { payload: emptyPayload("INVALID_KEY", now, nonce), failedAttempt: true };
  }

  const row = await settings.db.getByKey(licenseKey);
  if (!row) {
    return { payload: emptyPayload("INVALID_KEY", now, nonce), failedAttempt: true };
  }

  if (row.device_code !== deviceCode) {
    await settings.db.addEvent(
      row.id,
      now,
      "wrong_device_attempt",
      `${deviceCode} ${request.deviceModel ?? "unknown phone"}`.slice(0, 200),
    );
    return { payload: emptyPayload("WRONG_DEVICE", now, nonce), failedAttempt: true };
  }

  const patch: Partial<LicenseRow> = {};
  let reportedId: string | null = null;

  const usageCount = Math.max(0, Math.min(10_000, Math.trunc(request.usageCount ?? 0)));
  if (request.reportId) {
    const claimed = await settings.db.claimUsageReport(
      request.reportId,
      row.id,
      now,
      usageCount,
    );
    reportedId = request.reportId;
    if (claimed && usageCount > 0) {
      const today = dayKey(now, settings.quotaTz);
      const rolledOver = row.used_day !== today;
      patch.used_day = today;
      patch.used_total = row.used_total + usageCount;
      patch.used_today = (rolledOver ? 0 : row.used_today) + usageCount;
      row.used_total = patch.used_total;
      row.used_today = patch.used_today;
      row.used_day = today;
    }
  }

  const quota = quotaInfo(row, now, settings.quotaTz);
  const base = {
    name: row.name,
    expiresAt: row.expires_at,
    activatedAt: row.activated_at,
    serverTime: now,
    nonce,
    quota,
    reportedId,
  };

  if (row.status === "cancelled") {
    if (Object.keys(patch).length) await settings.db.update(row.id, patch);
    return { payload: { status: "CANCELLED", ...base }, failedAttempt: false };
  }
  if (row.status === "blocked") {
    if (Object.keys(patch).length) await settings.db.update(row.id, patch);
    return { payload: { status: "BLOCKED", ...base }, failedAttempt: false };
  }

  if (row.activated_at === null) {
    const activatedAt = now;
    const expiresAt =
      row.duration_days === null ? null : now + row.duration_days * DAY_MS;
    patch.activated_at = activatedAt;
    patch.expires_at = expiresAt;
    patch.last_seen_at = now;
    patch.device_model = request.deviceModel ?? null;
    patch.android_version = request.androidVersion ?? null;
    patch.app_version = request.appVersion ?? null;
    await settings.db.update(row.id, patch);
    await settings.db.addEvent(
      row.id,
      now,
      "activated",
      expiresAt === null ? "lifetime" : `expires ${new Date(expiresAt).toISOString()}`,
    );
    return {
      payload: {
        status: "ACTIVE",
        ...base,
        activatedAt,
        expiresAt,
      },
      failedAttempt: false,
    };
  }

  if (row.expires_at !== null && row.expires_at <= now) {
    patch.last_seen_at = now;
    await settings.db.update(row.id, patch);
    return { payload: { status: "EXPIRED", ...base }, failedAttempt: false };
  }

  patch.last_seen_at = now;
  patch.device_model = request.deviceModel ?? null;
  patch.android_version = request.androidVersion ?? null;
  patch.app_version = request.appVersion ?? null;
  await settings.db.update(row.id, patch);
  return { payload: { status: "ACTIVE", ...base }, failedAttempt: false };
}

export function planLabel(row: LicenseRow): string {
  if (row.quota_mode === "daily") return `${row.quota_limit ?? 0}/day`;
  if (row.quota_mode === "total") return `${row.quota_limit ?? 0} total`;
  return "Unlimited";
}

export function daysLeft(row: LicenseRow, now: number): number | null {
  if (row.expires_at === null) return null;
  return Math.max(0, Math.ceil((row.expires_at - now) / DAY_MS));
}

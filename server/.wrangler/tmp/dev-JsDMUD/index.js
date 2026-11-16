var __defProp = Object.defineProperty;
var __name = (target, value) => __defProp(target, "name", { value, configurable: true });

// src/db.ts
var COLUMNS = [
  "id",
  "license_key",
  "name",
  "note",
  "status",
  "device_code",
  "duration_days",
  "created_at",
  "activated_at",
  "expires_at",
  "device_model",
  "android_version",
  "app_version",
  "last_seen_at",
  "quota_mode",
  "quota_limit",
  "used_total",
  "used_today",
  "used_day"
];
var D1Db = class {
  static {
    __name(this, "D1Db");
  }
  d1;
  constructor(d1) {
    this.d1 = d1;
  }
  async getByKey(licenseKey) {
    return this.d1.prepare("SELECT * FROM licenses WHERE license_key = ?1").bind(licenseKey).first();
  }
  async getById(id) {
    return this.d1.prepare("SELECT * FROM licenses WHERE id = ?1").bind(id).first();
  }
  async getLiveByDevice(deviceCode) {
    return this.d1.prepare(
      "SELECT * FROM licenses WHERE device_code = ?1 AND status != 'cancelled' ORDER BY created_at DESC LIMIT 1"
    ).bind(deviceCode).first();
  }
  async list(filter) {
    const search = (filter.search ?? "").trim().toLowerCase();
    const rows = search ? await this.d1.prepare(
      "SELECT * FROM licenses WHERE LOWER(name) LIKE ?1 OR LOWER(license_key) LIKE ?1 OR LOWER(device_code) LIKE ?1 OR LOWER(IFNULL(note, '')) LIKE ?1 ORDER BY created_at DESC"
    ).bind(`%${search}%`).all() : await this.d1.prepare("SELECT * FROM licenses ORDER BY created_at DESC").all();
    return rows.results ?? [];
  }
  async insert(row) {
    const placeholders = COLUMNS.map((_, index) => `?${index + 1}`).join(", ");
    await this.d1.prepare(`INSERT INTO licenses (${COLUMNS.join(", ")}) VALUES (${placeholders})`).bind(...COLUMNS.map((column) => row[column] ?? null)).run();
  }
  async update(id, patch) {
    const columns = Object.keys(patch).filter(
      (key) => COLUMNS.includes(key)
    );
    if (columns.length === 0) return;
    const assignments = columns.map((column, index) => `${column} = ?${index + 1}`).join(", ");
    await this.d1.prepare(`UPDATE licenses SET ${assignments} WHERE id = ?${columns.length + 1}`).bind(...columns.map((column) => patch[column] ?? null), id).run();
  }
  async remove(id) {
    await this.d1.prepare("DELETE FROM licenses WHERE id = ?1").bind(id).run();
    await this.d1.prepare("DELETE FROM events WHERE license_id = ?1").bind(id).run();
  }
  async addEvent(licenseId, ts, type, detail) {
    await this.d1.prepare("INSERT INTO events (license_id, ts, type, detail) VALUES (?1, ?2, ?3, ?4)").bind(licenseId, ts, type, detail).run();
  }
  async events(licenseId, limit) {
    const rows = await this.d1.prepare(
      "SELECT id, license_id, ts, type, detail FROM events WHERE license_id = ?1 ORDER BY ts DESC, id DESC LIMIT ?2"
    ).bind(licenseId, limit).all();
    return rows.results ?? [];
  }
  async getSetting(key) {
    const row = await this.d1.prepare("SELECT value FROM settings WHERE key = ?1").bind(key).first();
    return row?.value ?? null;
  }
  async setSetting(key, value) {
    await this.d1.prepare(
      "INSERT INTO settings (key, value) VALUES (?1, ?2) ON CONFLICT(key) DO UPDATE SET value = excluded.value"
    ).bind(key, value).run();
  }
  async claimUsageReport(reportId, licenseId, ts, count) {
    const existing = await this.d1.prepare("SELECT report_id FROM usage_reports WHERE report_id = ?1").bind(reportId).first();
    if (existing) return false;
    await this.d1.prepare(
      "INSERT INTO usage_reports (report_id, license_id, ts, count) VALUES (?1, ?2, ?3, ?4)"
    ).bind(reportId, licenseId, ts, count).run();
    return true;
  }
  async purgeUsageReports(olderThan) {
    await this.d1.prepare("DELETE FROM usage_reports WHERE ts < ?1").bind(olderThan).run();
  }
  async bumpRate(bucket, resetAt) {
    await this.d1.prepare(
      "INSERT INTO rate_limits (bucket, count, reset_at) VALUES (?1, 1, ?2) ON CONFLICT(bucket) DO UPDATE SET count = CASE WHEN rate_limits.reset_at < ?3 THEN 1 ELSE rate_limits.count + 1 END, reset_at = CASE WHEN rate_limits.reset_at < ?3 THEN ?2 ELSE rate_limits.reset_at END"
    ).bind(bucket, resetAt, resetAt).run();
    const row = await this.d1.prepare("SELECT count FROM rate_limits WHERE bucket = ?1").bind(bucket).first();
    return row?.count ?? 1;
  }
  async pruneRates(now) {
    await this.d1.prepare("DELETE FROM rate_limits WHERE reset_at < ?1").bind(now).run();
  }
};
function displayStatus(row, now) {
  if (row.status === "cancelled") return "cancelled";
  if (row.status === "blocked") return "blocked";
  if (row.activated_at !== null && row.expires_at !== null && row.expires_at <= now) {
    return "expired";
  }
  if (row.activated_at === null) return "pending";
  return "active";
}
__name(displayStatus, "displayStatus");

// src/types.ts
var LICENSE_ALPHABET = "23456789ABCDEFGHJKLMNPQRSTUVWXYZ";
var DEVICE_CODE_PATTERN = /^TP-[2-9A-HJ-NP-Z]{4}(-[2-9A-HJ-NP-Z]{4}){3}$/;
var LICENSE_KEY_PATTERN = /^[2-9A-HJ-NP-Z]{4}(-[2-9A-HJ-NP-Z]{4}){3}$/;

// src/util.ts
var BASE64 = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/";
function base64UrlEncode(bytes) {
  let out = "";
  for (let i = 0; i < bytes.length; i += 3) {
    const b0 = bytes[i];
    const b1 = bytes[i + 1];
    const b2 = bytes[i + 2];
    out += BASE64[b0 >> 2];
    out += BASE64[(b0 & 3) << 4 | (b1 ?? 0) >> 4];
    if (b1 === void 0) break;
    out += BASE64[(b1 & 15) << 2 | (b2 ?? 0) >> 6];
    if (b2 === void 0) break;
    out += BASE64[b2 & 63];
  }
  return out.replace(/\+/g, "-").replace(/\//g, "_").replace(/=+$/, "");
}
__name(base64UrlEncode, "base64UrlEncode");
function base64UrlDecode(text) {
  const clean = text.replace(/-/g, "+").replace(/_/g, "/").replace(/=+$/, "");
  const out = new Uint8Array(Math.floor(clean.length * 3 / 4));
  let buffer = 0;
  let bits = 0;
  let index = 0;
  for (const char of clean) {
    const value = BASE64.indexOf(char);
    if (value < 0) throw new Error("invalid base64url");
    buffer = buffer << 6 | value;
    bits += 6;
    if (bits >= 8) {
      bits -= 8;
      out[index++] = buffer >> bits & 255;
    }
  }
  return out.subarray(0, index);
}
__name(base64UrlDecode, "base64UrlDecode");
function utf8(text) {
  return new TextEncoder().encode(text);
}
__name(utf8, "utf8");
function randomBytes(length) {
  const bytes = new Uint8Array(length);
  crypto.getRandomValues(bytes);
  return bytes;
}
__name(randomBytes, "randomBytes");
function randomId() {
  return Array.from(randomBytes(9), (b) => b.toString(16).padStart(2, "0")).join("");
}
__name(randomId, "randomId");
function generateLicenseKey() {
  const bytes = randomBytes(16);
  let text = "";
  for (const byte of bytes) text += LICENSE_ALPHABET[byte % 32];
  return group(text);
}
__name(generateLicenseKey, "generateLicenseKey");
function group(text, size = 4) {
  const parts = [];
  for (let i = 0; i < text.length; i += size) parts.push(text.slice(i, i + size));
  return parts.join("-");
}
__name(group, "group");
function normalizeLicenseKey(input) {
  if (typeof input !== "string") return null;
  const clean = input.toUpperCase().replace(/[^0-9A-Z]/g, "");
  if (clean.length !== 16) return null;
  const grouped = group(clean);
  return LICENSE_KEY_PATTERN.test(grouped) ? grouped : null;
}
__name(normalizeLicenseKey, "normalizeLicenseKey");
function normalizeDeviceCode(input) {
  if (typeof input !== "string") return null;
  const clean = input.toUpperCase().replace(/[^0-9A-Z]/g, "").replace(/^TP/, "");
  if (clean.length !== 16) return null;
  const formatted = `TP-${group(clean)}`;
  return DEVICE_CODE_PATTERN.test(formatted) ? formatted : null;
}
__name(normalizeDeviceCode, "normalizeDeviceCode");
function sameSecret(a, b) {
  const left = utf8(a);
  const right = utf8(b);
  let diff = left.length ^ right.length;
  const length = Math.max(left.length, right.length);
  for (let i = 0; i < length; i++) {
    diff |= (left[i] ?? 0) ^ (right[i] ?? 0);
  }
  return diff === 0;
}
__name(sameSecret, "sameSecret");
var formatters = /* @__PURE__ */ new Map();
function formatter(timeZone) {
  let existing = formatters.get(timeZone);
  if (!existing) {
    existing = new Intl.DateTimeFormat("en-US", {
      timeZone,
      hour12: false,
      year: "numeric",
      month: "2-digit",
      day: "2-digit",
      hour: "2-digit",
      minute: "2-digit",
      second: "2-digit"
    });
    formatters.set(timeZone, existing);
  }
  return existing;
}
__name(formatter, "formatter");
function wallClock(ts, timeZone) {
  const parts = {};
  for (const part of formatter(timeZone).formatToParts(new Date(ts))) {
    if (part.type !== "literal") parts[part.type] = Number(part.value);
  }
  return {
    year: parts.year,
    month: parts.month,
    day: parts.day,
    hour: parts.hour === 24 ? 0 : parts.hour,
    minute: parts.minute,
    second: parts.second
  };
}
__name(wallClock, "wallClock");
function offsetAt(ts, timeZone) {
  const wall = wallClock(ts, timeZone);
  const asUtc = Date.UTC(
    wall.year,
    wall.month - 1,
    wall.day,
    wall.hour,
    wall.minute,
    wall.second
  );
  return asUtc - Math.floor(ts / 1e3) * 1e3;
}
__name(offsetAt, "offsetAt");
function dayKey(ts, timeZone) {
  const wall = wallClock(ts, timeZone);
  return `${wall.year}-${String(wall.month).padStart(2, "0")}-${String(wall.day).padStart(2, "0")}`;
}
__name(dayKey, "dayKey");
function nextMidnight(ts, timeZone) {
  const wall = wallClock(ts, timeZone);
  const wallNextMidnight = Date.UTC(wall.year, wall.month - 1, wall.day + 1, 0, 0, 0);
  const firstGuess = wallNextMidnight - offsetAt(ts, timeZone);
  return wallNextMidnight - offsetAt(firstGuess, timeZone);
}
__name(nextMidnight, "nextMidnight");
function rawEcdsaToDer(raw) {
  if (raw.length !== 64) throw new Error("unexpected ECDSA signature length");
  const r = encodeDerInteger(trimZeros(raw.subarray(0, 32)));
  const s = encodeDerInteger(trimZeros(raw.subarray(32)));
  const body = new Uint8Array(r.length + s.length);
  body.set(r);
  body.set(s, r.length);
  return wrapDer(48, body);
}
__name(rawEcdsaToDer, "rawEcdsaToDer");
function trimZeros(value) {
  let start = 0;
  while (start < value.length - 1 && value[start] === 0) start++;
  return value.subarray(start);
}
__name(trimZeros, "trimZeros");
function encodeDerInteger(value) {
  const pad = (value[0] & 128) !== 0;
  const body = new Uint8Array(value.length + (pad ? 1 : 0));
  if (pad) body[0] = 0;
  body.set(value, pad ? 1 : 0);
  return wrapDer(2, body);
}
__name(encodeDerInteger, "encodeDerInteger");
function wrapDer(tag, body) {
  if (body.length > 127) {
    const out2 = new Uint8Array(3 + body.length);
    out2[0] = tag;
    out2[1] = 129;
    out2[2] = body.length;
    out2.set(body, 3);
    return out2;
  }
  const out = new Uint8Array(2 + body.length);
  out[0] = tag;
  out[1] = body.length;
  out.set(body, 2);
  return out;
}
__name(wrapDer, "wrapDer");

// src/licenses.ts
var HttpError = class extends Error {
  static {
    __name(this, "HttpError");
  }
  status;
  code;
  constructor(status, code, message) {
    super(message);
    this.status = status;
    this.code = code;
  }
};
var DAY_MS = 864e5;
function quotaInfo(row, now, timeZone) {
  const today = dayKey(now, timeZone);
  const rolledOver = row.used_day !== today;
  const usedToday = rolledOver ? 0 : row.used_today;
  const usedTotal = row.used_total;
  const limit = row.quota_mode === "unlimited" ? null : row.quota_limit;
  const used = row.quota_mode === "daily" ? usedToday : row.quota_mode === "total" ? usedTotal : null;
  return {
    mode: row.quota_mode,
    limit,
    usedToday,
    usedTotal,
    remaining: limit === null || used === null ? null : Math.max(0, limit - used),
    dayEndsAt: nextMidnight(now, timeZone)
  };
}
__name(quotaInfo, "quotaInfo");
async function createLicense(settings, input) {
  const name = input.name.trim();
  if (!name) throw new HttpError(400, "NAME_REQUIRED", "A customer name is required.");
  if (name.length > 64) throw new HttpError(400, "NAME_TOO_LONG", "Name is limited to 64 characters.");
  const deviceCode = normalizeDeviceCode(input.deviceCode);
  if (!deviceCode) {
    throw new HttpError(
      400,
      "INVALID_DEVICE_CODE",
      "That is not a valid Device Code. Expected TP-XXXX-XXXX-XXXX-XXXX."
    );
  }
  if (input.durationDays !== null) {
    if (!Number.isInteger(input.durationDays) || input.durationDays < 1 || input.durationDays > 36500) {
      throw new HttpError(400, "INVALID_DURATION", "Duration must be between 1 and 36500 days.");
    }
  }
  let quotaLimit = null;
  if (input.quotaMode !== "unlimited") {
    if (input.quotaLimit === null || !Number.isInteger(input.quotaLimit) || input.quotaLimit < 1 || input.quotaLimit > 1e6) {
      throw new HttpError(400, "INVALID_QUOTA", "The image limit must be between 1 and 1000000.");
    }
    quotaLimit = input.quotaLimit;
  }
  const existing = await settings.db.getLiveByDevice(deviceCode);
  if (existing) {
    throw new HttpError(
      409,
      "DEVICE_ALREADY_LICENSED",
      `This phone already has a license (${existing.name}, ${existing.license_key}). Extend that one instead.`
    );
  }
  let licenseKey = generateLicenseKey();
  for (let attempt = 0; attempt < 5; attempt++) {
    const clash = await settings.db.getByKey(licenseKey);
    if (!clash) break;
    licenseKey = generateLicenseKey();
  }
  const row = {
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
    used_day: null
  };
  await settings.db.insert(row);
  await settings.db.addEvent(row.id, settings.now, "created", `key ${licenseKey}`);
  return row;
}
__name(createLicense, "createLicense");
function emptyPayload(status, now, nonce, reportedId = null) {
  return {
    status,
    name: null,
    expiresAt: null,
    activatedAt: null,
    serverTime: now,
    nonce,
    quota: null,
    reportedId
  };
}
__name(emptyPayload, "emptyPayload");
async function verifyLicense(settings, request) {
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
      `${deviceCode} ${request.deviceModel ?? "unknown phone"}`.slice(0, 200)
    );
    return { payload: emptyPayload("WRONG_DEVICE", now, nonce), failedAttempt: true };
  }
  const patch = {};
  let reportedId = null;
  const usageCount = Math.max(0, Math.min(1e4, Math.trunc(request.usageCount ?? 0)));
  if (request.reportId) {
    const claimed = await settings.db.claimUsageReport(
      request.reportId,
      row.id,
      now,
      usageCount
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
    reportedId
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
    const expiresAt = row.duration_days === null ? null : now + row.duration_days * DAY_MS;
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
      expiresAt === null ? "lifetime" : `expires ${new Date(expiresAt).toISOString()}`
    );
    return {
      payload: {
        status: "ACTIVE",
        ...base,
        activatedAt,
        expiresAt
      },
      failedAttempt: false
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
__name(verifyLicense, "verifyLicense");
function planLabel(row) {
  if (row.quota_mode === "daily") return `${row.quota_limit ?? 0}/day`;
  if (row.quota_mode === "total") return `${row.quota_limit ?? 0} total`;
  return "Unlimited";
}
__name(planLabel, "planLabel");
function daysLeft(row, now) {
  if (row.expires_at === null) return null;
  return Math.max(0, Math.ceil((row.expires_at - now) / DAY_MS));
}
__name(daysLeft, "daysLeft");

// src/admin.ts
var DAY_MS2 = 864e5;
function toApi(row, now, timeZone) {
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
    daysLeft: daysLeft(row, now)
  };
}
__name(toApi, "toApi");
function toApiEvent(event) {
  return { ts: event.ts, type: event.type, detail: event.detail };
}
__name(toApiEvent, "toApiEvent");
async function requireLicense(settings, id) {
  const row = await settings.db.getById(id);
  if (!row) throw new HttpError(404, "NOT_FOUND", "No license with that id.");
  return row;
}
__name(requireLicense, "requireLicense");
async function listLicenses(settings, search, status) {
  const rows = await settings.db.list({ search });
  const wanted = (status ?? "").toLowerCase();
  const tz = settings.quotaTz;
  return rows.map((row) => toApi(row, settings.now, tz)).filter((row) => !wanted || wanted === "all" || row.status === wanted);
}
__name(listLicenses, "listLicenses");
async function licenseDetail(settings, id) {
  const row = await requireLicense(settings, id);
  const events = await settings.db.events(id, 100);
  return {
    license: toApi(row, settings.now, settings.quotaTz),
    events: events.map(toApiEvent)
  };
}
__name(licenseDetail, "licenseDetail");
async function setBlocked(settings, id, blocked) {
  const row = await requireLicense(settings, id);
  if (row.status === "cancelled") {
    throw new HttpError(409, "CANCELLED", "This license was cancelled and cannot change status.");
  }
  const status = blocked ? "blocked" : "active";
  await settings.db.update(id, { status });
  await settings.db.addEvent(id, settings.now, blocked ? "blocked" : "unblocked", null);
  return toApi({ ...row, status }, settings.now, settings.quotaTz);
}
__name(setBlocked, "setBlocked");
async function cancelLicense(settings, id) {
  const row = await requireLicense(settings, id);
  if (row.status === "cancelled") {
    throw new HttpError(409, "ALREADY_CANCELLED", "This license is already cancelled.");
  }
  await settings.db.update(id, { status: "cancelled" });
  await settings.db.addEvent(id, settings.now, "cancelled", null);
  return toApi({ ...row, status: "cancelled" }, settings.now, settings.quotaTz);
}
__name(cancelLicense, "cancelLicense");
async function movePhone(settings, id, deviceCodeInput) {
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
      `That phone already has a license (${other.name}).`
    );
  }
  await settings.db.update(id, { device_code: deviceCode });
  await settings.db.addEvent(
    id,
    settings.now,
    "moved_phone",
    `${row.device_code} -> ${deviceCode}`
  );
  return toApi({ ...row, device_code: deviceCode }, settings.now, settings.quotaTz);
}
__name(movePhone, "movePhone");
async function extendLicense(settings, id, body) {
  const row = await requireLicense(settings, id);
  const patch = {};
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
    if (!Number.isInteger(days) || days < 1 || days > 36500) {
      throw new HttpError(400, "INVALID_DURATION", "Days must be between 1 and 36500.");
    }
    const base = row.expires_at !== null && row.expires_at > settings.now ? row.expires_at : settings.now;
    patch.expires_at = base + days * DAY_MS2;
  }
  await settings.db.update(id, patch);
  await settings.db.addEvent(
    id,
    settings.now,
    "extended",
    patch.expires_at === null ? "lifetime" : `until ${new Date(patch.expires_at).toISOString()}`
  );
  return toApi({ ...row, ...patch }, settings.now, settings.quotaTz);
}
__name(extendLicense, "extendLicense");
async function patchLicense(settings, id, body) {
  const row = await requireLicense(settings, id);
  const patch = {};
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
__name(patchLicense, "patchLicense");
async function deleteLicense(settings, id) {
  const row = await requireLicense(settings, id);
  if (row.status !== "cancelled") {
    throw new HttpError(409, "NOT_CANCELLED", "Only cancelled licenses can be deleted.");
  }
  await settings.db.remove(id);
}
__name(deleteLicense, "deleteLicense");
async function setQuota(settings, id, modeInput, limitInput) {
  const row = await requireLicense(settings, id);
  if (modeInput !== "unlimited" && modeInput !== "daily" && modeInput !== "total") {
    throw new HttpError(400, "INVALID_QUOTA_MODE", "Mode must be unlimited, daily or total.");
  }
  const mode = modeInput;
  let limit = null;
  if (mode !== "unlimited") {
    const value = Number(limitInput);
    if (!Number.isInteger(value) || value < 1 || value > 1e6) {
      throw new HttpError(400, "INVALID_QUOTA", "The image limit must be between 1 and 1000000.");
    }
    limit = value;
  }
  await settings.db.update(id, { quota_mode: mode, quota_limit: limit });
  await settings.db.addEvent(
    id,
    settings.now,
    "quota_changed",
    mode === "unlimited" ? "unlimited" : `${mode} ${limit}`
  );
  return toApi({ ...row, quota_mode: mode, quota_limit: limit }, settings.now, settings.quotaTz);
}
__name(setQuota, "setQuota");
async function addUsage(settings, id, countInput) {
  const row = await requireLicense(settings, id);
  const count = Number(countInput);
  if (!Number.isInteger(count) || Math.abs(count) > 1e5) {
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
__name(addUsage, "addUsage");
async function resetUsage(settings, id, scope) {
  const row = await requireLicense(settings, id);
  if (scope !== "today" && scope !== "total") {
    throw new HttpError(400, "INVALID_SCOPE", "Scope must be today or total.");
  }
  const today = dayKey(settings.now, settings.quotaTz);
  const patch = scope === "today" ? { used_today: 0, used_day: today } : { used_today: 0, used_day: today, used_total: 0 };
  await settings.db.update(id, patch);
  await settings.db.addEvent(id, settings.now, "usage_reset", scope);
  return toApi({ ...row, ...patch }, settings.now, settings.quotaTz);
}
__name(resetUsage, "resetUsage");
async function stats(settings) {
  const rows = await settings.db.list({});
  const now = settings.now;
  const counts = { total: 0, active: 0, pending: 0, expired: 0, blocked: 0, cancelled: 0 };
  let expiringSoon = 0;
  let seenRecently = 0;
  for (const row of rows) {
    counts.total++;
    const status = displayStatus(row, now);
    if (status in counts) counts[status]++;
    if (row.status === "active" && row.expires_at !== null) {
      if (row.expires_at > now && row.expires_at <= now + 7 * DAY_MS2) expiringSoon++;
    }
    if (row.last_seen_at !== null && row.last_seen_at >= now - 48 * 36e5) seenRecently++;
  }
  return { ...counts, expiringSoon, seenRecently };
}
__name(stats, "stats");
function csvCell(value) {
  const text = value === null || value === void 0 ? "" : String(value);
  return /[",\n]/.test(text) ? `"${text.replace(/"/g, '""')}"` : text;
}
__name(csvCell, "csvCell");
async function exportCsv(settings) {
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
    "note"
  ];
  const lines = [header.join(",")];
  for (const row of rows) {
    const api = toApi(row, settings.now, tz);
    const iso = /* @__PURE__ */ __name((ts) => ts === null ? "" : new Date(ts).toISOString(), "iso");
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
        api.note
      ].map(csvCell).join(",")
    );
  }
  return lines.join("\n") + "\n";
}
__name(exportCsv, "exportCsv");

// src/signing.ts
var ALGORITHM = { name: "ECDSA", namedCurve: "P-256" };
async function importPrivateKeyPem(pem) {
  const base64 = pem.replace(/-----BEGIN [^-]+-----/g, "").replace(/-----END [^-]+-----/g, "").replace(/\s+/g, "");
  if (!base64) throw new Error("empty signing key");
  const der = base64UrlDecode(base64);
  return crypto.subtle.importKey("pkcs8", der, ALGORITHM, false, ["sign"]);
}
__name(importPrivateKeyPem, "importPrivateKeyPem");
async function signPayload(key, payload) {
  const bytes = utf8(JSON.stringify(payload));
  const raw = new Uint8Array(
    await crypto.subtle.sign({ name: "ECDSA", hash: "SHA-256" }, key, bytes)
  );
  return { payload: base64UrlEncode(bytes), sig: base64UrlEncode(rawEcdsaToDer(raw)) };
}
__name(signPayload, "signPayload");

// src/index.ts
var MAX_FAILED_ATTEMPTS_PER_HOUR = 10;
function json(body, status = 200) {
  return new Response(JSON.stringify(body), {
    status,
    headers: {
      "content-type": "application/json; charset=utf-8",
      "cache-control": "no-store"
    }
  });
}
__name(json, "json");
async function readJson(request) {
  const text = await request.text();
  if (!text.trim()) return {};
  try {
    const parsed = JSON.parse(text);
    if (parsed === null || typeof parsed !== "object" || Array.isArray(parsed)) {
      throw new Error("not an object");
    }
    return parsed;
  } catch {
    throw new HttpError(400, "INVALID_JSON", "The request body must be a JSON object.");
  }
}
__name(readJson, "readJson");
function createApp(deps) {
  return /* @__PURE__ */ __name(async function handle(request) {
    try {
      const url = new URL(request.url);
      const path = url.pathname.replace(/\/+$/, "") || "/";
      const method = request.method.toUpperCase();
      if (path === "/health") {
        return json({ ok: true, serverTime: deps.now() });
      }
      if (path === "/v1/verify" && method === "POST") {
        return await handleVerify(deps, request);
      }
      if (path === "/admin" || path.startsWith("/admin/")) {
        const auth = request.headers.get("authorization") ?? "";
        const token = auth.startsWith("Bearer ") ? auth.slice(7).trim() : "";
        if (!deps.adminToken || !sameSecret(token, deps.adminToken)) {
          return json({ error: "UNAUTHORIZED", message: "Admin token missing or wrong." }, 401);
        }
        return await handleAdmin(deps, path, method, request);
      }
      return json({ error: "NOT_FOUND", message: "Unknown endpoint." }, 404);
    } catch (error) {
      if (error instanceof HttpError) {
        return json({ error: error.code, message: error.message }, error.status);
      }
      const message = error instanceof Error ? error.message : "unexpected error";
      return json({ error: "SERVER_ERROR", message }, 500);
    }
  }, "handle");
}
__name(createApp, "createApp");
async function handleVerify(deps, request) {
  if (!deps.signingKey) {
    return json(
      {
        error: "NOT_CONFIGURED",
        message: "The SIGNING key secret is missing on the server."
      },
      503
    );
  }
  const body = await readJson(request);
  const nonce = typeof body.nonce === "string" ? body.nonce.slice(0, 64) : "";
  if (!nonce) {
    return json({ error: "NONCE_REQUIRED", message: "A nonce is required." }, 400);
  }
  const verifyRequest = {
    key: typeof body.key === "string" ? body.key : "",
    deviceCode: typeof body.deviceCode === "string" ? body.deviceCode : "",
    deviceModel: typeof body.deviceModel === "string" ? body.deviceModel.slice(0, 80) : null,
    androidVersion: typeof body.androidVersion === "string" ? body.androidVersion.slice(0, 32) : null,
    appVersion: typeof body.appVersion === "string" ? body.appVersion.slice(0, 32) : null,
    nonce,
    usageCount: Number(body.usageCount ?? 0) || 0,
    reportId: typeof body.reportId === "string" ? body.reportId.slice(0, 64) : null
  };
  const now = deps.now();
  const quotaTz = await deps.db.getSetting("quota_tz") ?? deps.quotaTz;
  const outcome = await verifyLicense({ db: deps.db, now, quotaTz }, verifyRequest);
  if (outcome.failedAttempt) {
    const hour = 36e5;
    const bucket = `${deps.clientIp(request)}|${verifyRequest.deviceCode}|${Math.floor(now / hour)}`;
    const count = await deps.db.bumpRate(bucket, (Math.floor(now / hour) + 1) * hour);
    if (count === 1) await deps.db.pruneRates(now);
    if (count > MAX_FAILED_ATTEMPTS_PER_HOUR) {
      return json(
        {
          error: "RATE_LIMITED",
          message: "Too many activation attempts. Try again in an hour."
        },
        429
      );
    }
  }
  return json(await signPayload(deps.signingKey, outcome.payload));
}
__name(handleVerify, "handleVerify");
async function handleAdmin(deps, path, method, request) {
  const now = deps.now();
  const quotaTz = await deps.db.getSetting("quota_tz") ?? deps.quotaTz;
  const settings = { db: deps.db, now, quotaTz };
  if (path === "/admin/licenses" && method === "GET") {
    const url = new URL(request.url);
    const items = await listLicenses(
      settings,
      url.searchParams.get("search") ?? "",
      url.searchParams.get("status") ?? ""
    );
    return json({ licenses: items, serverTime: now });
  }
  if (path === "/admin/licenses" && method === "POST") {
    const body2 = await readJson(request);
    const quotaMode = body2.quotaMode === "daily" || body2.quotaMode === "total" ? body2.quotaMode : "unlimited";
    const created = await createLicense(settings, {
      name: String(body2.name ?? ""),
      deviceCode: String(body2.deviceCode ?? ""),
      durationDays: body2.durationDays === null || body2.durationDays === void 0 ? null : Number(body2.durationDays),
      note: typeof body2.note === "string" ? body2.note : null,
      quotaMode,
      quotaLimit: body2.quotaLimit === null || body2.quotaLimit === void 0 ? null : Number(body2.quotaLimit)
    });
    return json({ license: (await licenseDetail(settings, created.id)).license }, 201);
  }
  if (path === "/admin/stats" && method === "GET") {
    return json({ ...await stats(settings), serverTime: now });
  }
  if (path === "/admin/export.csv" && method === "GET") {
    return new Response(await exportCsv(settings), {
      headers: {
        "content-type": "text/csv; charset=utf-8",
        "content-disposition": 'attachment; filename="textpic-licenses.csv"'
      }
    });
  }
  if (path === "/admin/settings" && method === "GET") {
    return json({ quotaTz });
  }
  if (path === "/admin/settings" && method === "PUT") {
    const body2 = await readJson(request);
    const timeZone = String(body2.quotaTz ?? "").trim();
    try {
      new Intl.DateTimeFormat("en-US", { timeZone });
    } catch {
      return json({ error: "INVALID_TIME_ZONE", message: "Unknown time zone name." }, 400);
    }
    await deps.db.setSetting("quota_tz", timeZone);
    return json({ quotaTz: timeZone });
  }
  const match = /^\/admin\/licenses\/([^/]+)(?:\/([a-z/-]+))?$/.exec(path);
  if (!match) return json({ error: "NOT_FOUND", message: "Unknown endpoint." }, 404);
  const id = decodeURIComponent(match[1]);
  const action = match[2] ?? "";
  if (!action && method === "GET") {
    return json(await licenseDetail(settings, id));
  }
  if (!action && method === "PATCH") {
    const body2 = await readJson(request);
    return json({ license: await patchLicense(settings, id, body2) });
  }
  if (!action && method === "DELETE") {
    await deleteLicense(settings, id);
    return json({ ok: true });
  }
  if (method !== "POST") {
    return json({ error: "METHOD_NOT_ALLOWED", message: "Use GET, PATCH, DELETE or POST." }, 405);
  }
  const body = await readJson(request);
  switch (action) {
    case "block":
      return json({ license: await setBlocked(settings, id, true) });
    case "unblock":
      return json({ license: await setBlocked(settings, id, false) });
    case "cancel":
      return json({ license: await cancelLicense(settings, id) });
    case "move-phone":
      return json({ license: await movePhone(settings, id, body.deviceCode) });
    case "extend":
      return json({ license: await extendLicense(settings, id, body) });
    case "quota":
      return json({ license: await setQuota(settings, id, body.mode, body.limit) });
    case "usage/add":
      return json({ license: await addUsage(settings, id, body.count) });
    case "usage/reset":
      return json({ license: await resetUsage(settings, id, body.scope) });
    default:
      return json({ error: "NOT_FOUND", message: "Unknown license action." }, 404);
  }
}
__name(handleAdmin, "handleAdmin");
var cachedKeySource = null;
var cachedKey = null;
async function signingKeyFrom(secret) {
  if (!secret) return null;
  if (cachedKeySource === secret && cachedKey) return cachedKey;
  const imported = await importPrivateKeyPem(secret);
  cachedKeySource = secret;
  cachedKey = imported;
  return imported;
}
__name(signingKeyFrom, "signingKeyFrom");
var src_default = {
  async fetch(request, env) {
    const signingKey = await signingKeyFrom(env.LICENSE_SIGNING_KEY);
    const deps = {
      db: new D1Db(env.DB),
      adminToken: env.ADMIN_TOKEN ?? "",
      quotaTz: env.QUOTA_TZ ?? "UTC",
      now: /* @__PURE__ */ __name(() => Date.now(), "now"),
      signingKey,
      clientIp: /* @__PURE__ */ __name((req) => req.headers.get("cf-connecting-ip") ?? req.headers.get("x-forwarded-for")?.split(",")[0].trim() ?? "local", "clientIp")
    };
    return createApp(deps)(request);
  }
};

// node_modules/wrangler/templates/middleware/middleware-ensure-req-body-drained.ts
var drainBody = /* @__PURE__ */ __name(async (request, env, _ctx, middlewareCtx) => {
  try {
    return await middlewareCtx.next(request, env);
  } finally {
    try {
      if (request.body !== null && !request.bodyUsed) {
        const reader = request.body.getReader();
        while (!(await reader.read()).done) {
        }
      }
    } catch (e) {
      console.error("Failed to drain the unused request body.", e);
    }
  }
}, "drainBody");
var middleware_ensure_req_body_drained_default = drainBody;

// node_modules/wrangler/templates/middleware/middleware-miniflare3-json-error.ts
function reduceError(e) {
  return {
    name: e?.name,
    message: e?.message ?? String(e),
    stack: e?.stack,
    cause: e?.cause === void 0 ? void 0 : reduceError(e.cause)
  };
}
__name(reduceError, "reduceError");
var jsonError = /* @__PURE__ */ __name(async (request, env, _ctx, middlewareCtx) => {
  try {
    return await middlewareCtx.next(request, env);
  } catch (e) {
    const error = reduceError(e);
    const body = JSON.stringify(error);
    const headers = {
      "Content-Type": "application/json",
      "MF-Experimental-Error-Stack": "true"
    };
    const encoded = encodeURIComponent(body);
    if (encoded.length <= 8192) {
      headers["MF-Experimental-Error-Stack-Payload"] = encoded;
    }
    return new Response(body, { status: 500, headers });
  }
}, "jsonError");
var middleware_miniflare3_json_error_default = jsonError;

// .wrangler/tmp/bundle-XZG3PM/middleware-insertion-facade.js
var __INTERNAL_WRANGLER_MIDDLEWARE__ = [
  middleware_ensure_req_body_drained_default,
  middleware_miniflare3_json_error_default
];
var middleware_insertion_facade_default = src_default;

// node_modules/wrangler/templates/middleware/common.ts
var __facade_middleware__ = [];
function __facade_register__(...args) {
  __facade_middleware__.push(...args.flat());
}
__name(__facade_register__, "__facade_register__");
function __facade_invokeChain__(request, env, ctx, dispatch, middlewareChain) {
  const [head, ...tail] = middlewareChain;
  const middlewareCtx = {
    dispatch,
    next(newRequest, newEnv) {
      return __facade_invokeChain__(newRequest, newEnv, ctx, dispatch, tail);
    }
  };
  return head(request, env, ctx, middlewareCtx);
}
__name(__facade_invokeChain__, "__facade_invokeChain__");
function __facade_invoke__(request, env, ctx, dispatch, finalMiddleware) {
  return __facade_invokeChain__(request, env, ctx, dispatch, [
    ...__facade_middleware__,
    finalMiddleware
  ]);
}
__name(__facade_invoke__, "__facade_invoke__");

// .wrangler/tmp/bundle-XZG3PM/middleware-loader.entry.ts
var __Facade_ScheduledController__ = class ___Facade_ScheduledController__ {
  constructor(scheduledTime, cron, noRetry) {
    this.scheduledTime = scheduledTime;
    this.cron = cron;
    this.#noRetry = noRetry;
  }
  scheduledTime;
  cron;
  static {
    __name(this, "__Facade_ScheduledController__");
  }
  #noRetry;
  noRetry() {
    if (!(this instanceof ___Facade_ScheduledController__)) {
      throw new TypeError("Illegal invocation");
    }
    this.#noRetry();
  }
};
function wrapExportedHandler(worker) {
  if (__INTERNAL_WRANGLER_MIDDLEWARE__ === void 0 || __INTERNAL_WRANGLER_MIDDLEWARE__.length === 0) {
    return worker;
  }
  for (const middleware of __INTERNAL_WRANGLER_MIDDLEWARE__) {
    __facade_register__(middleware);
  }
  const fetchDispatcher = /* @__PURE__ */ __name(function(request, env, ctx) {
    if (worker.fetch === void 0) {
      throw new Error("Handler does not export a fetch() function.");
    }
    return worker.fetch(request, env, ctx);
  }, "fetchDispatcher");
  return {
    ...worker,
    fetch(request, env, ctx) {
      const dispatcher = /* @__PURE__ */ __name(function(type, init) {
        if (type === "scheduled" && worker.scheduled !== void 0) {
          const controller = new __Facade_ScheduledController__(
            Date.now(),
            init.cron ?? "",
            () => {
            }
          );
          return worker.scheduled(controller, env, ctx);
        }
      }, "dispatcher");
      return __facade_invoke__(request, env, ctx, dispatcher, fetchDispatcher);
    }
  };
}
__name(wrapExportedHandler, "wrapExportedHandler");
function wrapWorkerEntrypoint(klass) {
  if (__INTERNAL_WRANGLER_MIDDLEWARE__ === void 0 || __INTERNAL_WRANGLER_MIDDLEWARE__.length === 0) {
    return klass;
  }
  for (const middleware of __INTERNAL_WRANGLER_MIDDLEWARE__) {
    __facade_register__(middleware);
  }
  return class extends klass {
    #fetchDispatcher = /* @__PURE__ */ __name((request, env, ctx) => {
      this.env = env;
      this.ctx = ctx;
      if (super.fetch === void 0) {
        throw new Error("Entrypoint class does not define a fetch() function.");
      }
      return super.fetch(request);
    }, "#fetchDispatcher");
    #dispatcher = /* @__PURE__ */ __name((type, init) => {
      if (type === "scheduled" && super.scheduled !== void 0) {
        const controller = new __Facade_ScheduledController__(
          Date.now(),
          init.cron ?? "",
          () => {
          }
        );
        return super.scheduled(controller);
      }
    }, "#dispatcher");
    fetch(request) {
      return __facade_invoke__(
        request,
        this.env,
        this.ctx,
        this.#dispatcher,
        this.#fetchDispatcher
      );
    }
  };
}
__name(wrapWorkerEntrypoint, "wrapWorkerEntrypoint");
var WRAPPED_ENTRY;
if (typeof middleware_insertion_facade_default === "object") {
  WRAPPED_ENTRY = wrapExportedHandler(middleware_insertion_facade_default);
} else if (typeof middleware_insertion_facade_default === "function") {
  WRAPPED_ENTRY = wrapWorkerEntrypoint(middleware_insertion_facade_default);
}
var middleware_loader_entry_default = WRAPPED_ENTRY;
export {
  __INTERNAL_WRANGLER_MIDDLEWARE__,
  createApp,
  middleware_loader_entry_default as default
};
//# sourceMappingURL=index.js.map

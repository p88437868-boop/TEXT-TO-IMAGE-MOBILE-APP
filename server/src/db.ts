import type { EventRow, LicenseRow } from "./types.ts";

export interface ListFilter {
  search?: string;
}

/** Minimal shape of the D1 binding (no @cloudflare/workers-types needed). */
export interface D1Result<T> {
  results?: T[];
}
export interface D1Statement {
  bind(...values: unknown[]): D1Statement;
  first<T>(): Promise<T | null>;
  all<T>(): Promise<D1Result<T>>;
  run(): Promise<unknown>;
}
export interface D1Database {
  prepare(sql: string): D1Statement;
}

export interface Db {
  getByKey(licenseKey: string): Promise<LicenseRow | null>;
  getById(id: string): Promise<LicenseRow | null>;
  /** Any license for this phone that is not cancelled (duplicate check). */
  getLiveByDevice(deviceCode: string): Promise<LicenseRow | null>;
  list(filter: ListFilter): Promise<LicenseRow[]>;
  insert(row: LicenseRow): Promise<void>;
  update(id: string, patch: Partial<LicenseRow>): Promise<void>;
  remove(id: string): Promise<void>;
  addEvent(licenseId: string, ts: number, type: string, detail: string | null): Promise<void>;
  events(licenseId: string, limit: number): Promise<EventRow[]>;
  getSetting(key: string): Promise<string | null>;
  setSetting(key: string, value: string): Promise<void>;
  /** Returns false when this report id was already counted. */
  claimUsageReport(reportId: string, licenseId: string, ts: number, count: number): Promise<boolean>;
  purgeUsageReports(olderThan: number): Promise<void>;
  /** Increments a bucket and returns the new count. */
  bumpRate(bucket: string, resetAt: number): Promise<number>;
  pruneRates(now: number): Promise<void>;
}

export const COLUMNS: (keyof LicenseRow)[] = [
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
  "used_day",
];

/** Cloudflare D1 implementation. */
export class D1Db implements Db {
  private readonly d1: D1Database;

  constructor(d1: D1Database) {
    this.d1 = d1;
  }

  async getByKey(licenseKey: string): Promise<LicenseRow | null> {
    return this.d1
      .prepare("SELECT * FROM licenses WHERE license_key = ?1")
      .bind(licenseKey)
      .first<LicenseRow>();
  }

  async getById(id: string): Promise<LicenseRow | null> {
    return this.d1.prepare("SELECT * FROM licenses WHERE id = ?1").bind(id).first<LicenseRow>();
  }

  async getLiveByDevice(deviceCode: string): Promise<LicenseRow | null> {
    return this.d1
      .prepare(
        "SELECT * FROM licenses WHERE device_code = ?1 AND status != 'cancelled' ORDER BY created_at DESC LIMIT 1",
      )
      .bind(deviceCode)
      .first<LicenseRow>();
  }

  async list(filter: ListFilter): Promise<LicenseRow[]> {
    const search = (filter.search ?? "").trim().toLowerCase();
    const rows = search
      ? await this.d1
          .prepare(
            "SELECT * FROM licenses WHERE LOWER(name) LIKE ?1 OR LOWER(license_key) LIKE ?1 OR LOWER(device_code) LIKE ?1 OR LOWER(IFNULL(note, '')) LIKE ?1 ORDER BY created_at DESC",
          )
          .bind(`%${search}%`)
          .all<LicenseRow>()
      : await this.d1
          .prepare("SELECT * FROM licenses ORDER BY created_at DESC")
          .all<LicenseRow>();
    return rows.results ?? [];
  }

  async insert(row: LicenseRow): Promise<void> {
    const placeholders = COLUMNS.map((_, index) => `?${index + 1}`).join(", ");
    await this.d1
      .prepare(`INSERT INTO licenses (${COLUMNS.join(", ")}) VALUES (${placeholders})`)
      .bind(...COLUMNS.map((column) => row[column] ?? null))
      .run();
  }

  async update(id: string, patch: Partial<LicenseRow>): Promise<void> {
    const columns = Object.keys(patch).filter((key): key is keyof LicenseRow =>
      COLUMNS.includes(key as keyof LicenseRow),
    );
    if (columns.length === 0) return;
    const assignments = columns.map((column, index) => `${column} = ?${index + 1}`).join(", ");
    await this.d1
      .prepare(`UPDATE licenses SET ${assignments} WHERE id = ?${columns.length + 1}`)
      .bind(...columns.map((column) => patch[column] ?? null), id)
      .run();
  }

  async remove(id: string): Promise<void> {
    await this.d1.prepare("DELETE FROM licenses WHERE id = ?1").bind(id).run();
    await this.d1.prepare("DELETE FROM events WHERE license_id = ?1").bind(id).run();
  }

  async addEvent(
    licenseId: string,
    ts: number,
    type: string,
    detail: string | null,
  ): Promise<void> {
    await this.d1
      .prepare("INSERT INTO events (license_id, ts, type, detail) VALUES (?1, ?2, ?3, ?4)")
      .bind(licenseId, ts, type, detail)
      .run();
  }

  async events(licenseId: string, limit: number): Promise<EventRow[]> {
    const rows = await this.d1
      .prepare(
        "SELECT id, license_id, ts, type, detail FROM events WHERE license_id = ?1 ORDER BY ts DESC, id DESC LIMIT ?2",
      )
      .bind(licenseId, limit)
      .all<EventRow>();
    return rows.results ?? [];
  }

  async getSetting(key: string): Promise<string | null> {
    const row = await this.d1
      .prepare("SELECT value FROM settings WHERE key = ?1")
      .bind(key)
      .first<{ value: string }>();
    return row?.value ?? null;
  }

  async setSetting(key: string, value: string): Promise<void> {
    await this.d1
      .prepare(
        "INSERT INTO settings (key, value) VALUES (?1, ?2) ON CONFLICT(key) DO UPDATE SET value = excluded.value",
      )
      .bind(key, value)
      .run();
  }

  async claimUsageReport(
    reportId: string,
    licenseId: string,
    ts: number,
    count: number,
  ): Promise<boolean> {
    const existing = await this.d1
      .prepare("SELECT report_id FROM usage_reports WHERE report_id = ?1")
      .bind(reportId)
      .first<{ report_id: string }>();
    if (existing) return false;
    await this.d1
      .prepare(
        "INSERT INTO usage_reports (report_id, license_id, ts, count) VALUES (?1, ?2, ?3, ?4)",
      )
      .bind(reportId, licenseId, ts, count)
      .run();
    return true;
  }

  async purgeUsageReports(olderThan: number): Promise<void> {
    await this.d1.prepare("DELETE FROM usage_reports WHERE ts < ?1").bind(olderThan).run();
  }

  async bumpRate(bucket: string, resetAt: number): Promise<number> {
    await this.d1
      .prepare(
        "INSERT INTO rate_limits (bucket, count, reset_at) VALUES (?1, 1, ?2) " +
          "ON CONFLICT(bucket) DO UPDATE SET count = CASE WHEN rate_limits.reset_at < ?3 THEN 1 ELSE rate_limits.count + 1 END, " +
          "reset_at = CASE WHEN rate_limits.reset_at < ?3 THEN ?2 ELSE rate_limits.reset_at END",
      )
      .bind(bucket, resetAt, resetAt)
      .run();
    const row = await this.d1
      .prepare("SELECT count FROM rate_limits WHERE bucket = ?1")
      .bind(bucket)
      .first<{ count: number }>();
    return row?.count ?? 1;
  }

  async pruneRates(now: number): Promise<void> {
    await this.d1.prepare("DELETE FROM rate_limits WHERE reset_at < ?1").bind(now).run();
  }
}

/** One of pending | active | expired | blocked | cancelled. */
export function displayStatus(row: LicenseRow, now: number): string {
  if (row.status === "cancelled") return "cancelled";
  if (row.status === "blocked") return "blocked";
  if (row.activated_at !== null && row.expires_at !== null && row.expires_at <= now) {
    return "expired";
  }
  if (row.activated_at === null) return "pending";
  return "active";
}

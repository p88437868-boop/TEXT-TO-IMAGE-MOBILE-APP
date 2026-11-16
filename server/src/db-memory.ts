import type { Db, ListFilter } from "./db.ts";
import type { EventRow, LicenseRow } from "./types.ts";

/** In-memory database used by the tests (and useful for quick experiments). */
export class MemoryDb implements Db {
  private licenses = new Map<string, LicenseRow>();
  private eventRows: EventRow[] = [];
  private settings = new Map<string, string>();
  private reports = new Set<string>();
  private rates = new Map<string, { count: number; resetAt: number }>();

  async getByKey(licenseKey: string): Promise<LicenseRow | null> {
    for (const row of this.licenses.values()) {
      if (row.license_key === licenseKey) return { ...row };
    }
    return null;
  }

  async getById(id: string): Promise<LicenseRow | null> {
    const row = this.licenses.get(id);
    return row ? { ...row } : null;
  }

  async getLiveByDevice(deviceCode: string): Promise<LicenseRow | null> {
    const rows = [...this.licenses.values()]
      .filter((row) => row.device_code === deviceCode && row.status !== "cancelled")
      .sort((a, b) => b.created_at - a.created_at);
    return rows[0] ? { ...rows[0] } : null;
  }

  async list(filter: ListFilter): Promise<LicenseRow[]> {
    const search = (filter.search ?? "").trim().toLowerCase();
    return [...this.licenses.values()]
      .filter((row) => {
        if (!search) return true;
        return (
          row.name.toLowerCase().includes(search) ||
          row.license_key.toLowerCase().includes(search) ||
          row.device_code.toLowerCase().includes(search) ||
          (row.note ?? "").toLowerCase().includes(search)
        );
      })
      .sort((a, b) => b.created_at - a.created_at)
      .map((row) => ({ ...row }));
  }

  async insert(row: LicenseRow): Promise<void> {
    this.licenses.set(row.id, { ...row });
  }

  async update(id: string, patch: Partial<LicenseRow>): Promise<void> {
    const row = this.licenses.get(id);
    if (!row) return;
    this.licenses.set(id, { ...row, ...patch });
  }

  async remove(id: string): Promise<void> {
    this.licenses.delete(id);
    this.eventRows = this.eventRows.filter((event) => event.license_id !== id);
  }

  async addEvent(
    licenseId: string,
    ts: number,
    type: string,
    detail: string | null,
  ): Promise<void> {
    this.eventRows.push({ license_id: licenseId, ts, type, detail });
  }

  async events(licenseId: string, limit: number): Promise<EventRow[]> {
    return this.eventRows
      .filter((event) => event.license_id === licenseId)
      .sort((a, b) => b.ts - a.ts)
      .slice(0, limit)
      .map((event) => ({ ...event }));
  }

  async getSetting(key: string): Promise<string | null> {
    return this.settings.get(key) ?? null;
  }

  async setSetting(key: string, value: string): Promise<void> {
    this.settings.set(key, value);
  }

  async claimUsageReport(
    reportId: string,
    _licenseId: string,
    _ts: number,
    _count: number,
  ): Promise<boolean> {
    if (this.reports.has(reportId)) return false;
    this.reports.add(reportId);
    return true;
  }

  async purgeUsageReports(_olderThan: number): Promise<void> {
    // Memory database lives for one test only.
  }

  async bumpRate(bucket: string, resetAt: number): Promise<number> {
    const existing = this.rates.get(bucket);
    const count = !existing || existing.resetAt < resetAt ? 1 : existing.count + 1;
    this.rates.set(bucket, { count, resetAt });
    return count;
  }

  async pruneRates(_now: number): Promise<void> {
    // Memory database lives for one test only.
  }

  allEvents(): EventRow[] {
    return this.eventRows.map((event) => ({ ...event }));
  }
}

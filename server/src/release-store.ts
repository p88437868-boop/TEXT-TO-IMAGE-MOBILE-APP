import type { D1Database } from "./db.ts";
import type { ReleaseRow, ReleaseStore, StoredChunk } from "./releases.ts";

/** Cloudflare D1 storage for releases. Chunks are BLOBs, never base64. */
export class D1ReleaseStore implements ReleaseStore {
  private readonly d1: D1Database;

  constructor(d1: D1Database) {
    this.d1 = d1;
  }

  async list(): Promise<ReleaseRow[]> {
    const rows = await this.d1
      .prepare("SELECT * FROM releases ORDER BY version_code DESC")
      .all<ReleaseRow>();
    return rows.results ?? [];
  }

  async get(versionCode: number): Promise<ReleaseRow | null> {
    return this.d1
      .prepare("SELECT * FROM releases WHERE version_code = ?1")
      .bind(versionCode)
      .first<ReleaseRow>();
  }

  async insert(row: ReleaseRow): Promise<void> {
    const columns = [
      "version_code",
      "version_name",
      "notes",
      "sha256",
      "size_bytes",
      "chunk_count",
      "chunk_size",
      "mandatory",
      "status",
      "created_at",
      "published_at",
    ];
    const placeholders = columns.map((_, index) => `?${index + 1}`).join(", ");
    await this.d1
      .prepare(`INSERT INTO releases (${columns.join(", ")}) VALUES (${placeholders})`)
      .bind(...columns.map((column) => (row as unknown as Record<string, unknown>)[column] ?? null))
      .run();
  }

  async update(versionCode: number, patch: Partial<ReleaseRow>): Promise<void> {
    const columns = Object.keys(patch).filter((key) =>
      [
        "version_name",
        "notes",
        "sha256",
        "size_bytes",
        "chunk_count",
        "chunk_size",
        "mandatory",
        "status",
        "published_at",
      ].includes(key),
    );
    if (columns.length === 0) return;
    const assignments = columns.map((column, index) => `${column} = ?${index + 1}`).join(", ");
    await this.d1
      .prepare(`UPDATE releases SET ${assignments} WHERE version_code = ?${columns.length + 1}`)
      .bind(
        ...columns.map((column) => (patch as unknown as Record<string, unknown>)[column] ?? null),
        versionCode,
      )
      .run();
  }

  async remove(versionCode: number): Promise<void> {
    await this.d1.prepare("DELETE FROM releases WHERE version_code = ?1").bind(versionCode).run();
  }

  async putChunk(
    versionCode: number,
    index: number,
    sha256: string,
    data: ArrayBuffer,
  ): Promise<void> {
    await this.d1
      .prepare(
        "INSERT INTO release_chunks (version_code, idx, sha256, data) VALUES (?1, ?2, ?3, ?4) " +
          "ON CONFLICT(version_code, idx) DO UPDATE SET sha256 = excluded.sha256, data = excluded.data",
      )
      .bind(versionCode, index, sha256, data)
      .run();
  }

  async storedIndexes(versionCode: number): Promise<number[]> {
    const rows = await this.d1
      .prepare("SELECT idx FROM release_chunks WHERE version_code = ?1 ORDER BY idx")
      .bind(versionCode)
      .all<{ idx: number }>();
    return (rows.results ?? []).map((row) => row.idx);
  }

  async chunk(versionCode: number, index: number): Promise<StoredChunk | null> {
    const row = await this.d1
      .prepare("SELECT sha256, data FROM release_chunks WHERE version_code = ?1 AND idx = ?2")
      .bind(versionCode, index)
      .first<{ sha256: string; data: ArrayBuffer | number[] }>();
    if (!row) return null;
    const data = row.data instanceof ArrayBuffer ? row.data : new Uint8Array(row.data).buffer;
    return { sha256: row.sha256, data };
  }

  async deleteChunks(versionCode: number): Promise<void> {
    await this.d1
      .prepare("DELETE FROM release_chunks WHERE version_code = ?1")
      .bind(versionCode)
      .run();
  }

  async addEvent(
    versionCode: number,
    ts: number,
    type: string,
    detail: string | null,
  ): Promise<void> {
    await this.d1
      .prepare(
        "INSERT INTO release_events (version_code, ts, type, detail) VALUES (?1, ?2, ?3, ?4)",
      )
      .bind(versionCode, ts, type, detail)
      .run();
  }

  async events(limit: number) {
    const rows = await this.d1
      .prepare(
        "SELECT version_code, ts, type, detail FROM release_events ORDER BY ts DESC, id DESC LIMIT ?1",
      )
      .bind(limit)
      .all<{ version_code: number; ts: number; type: string; detail: string | null }>();
    return rows.results ?? [];
  }
}

/** The test double: same behaviour, no D1. */
export class MemoryReleaseStore implements ReleaseStore {
  private releases = new Map<number, ReleaseRow>();
  private chunks = new Map<string, { sha256: string; data: ArrayBuffer }>();
  private log: { version_code: number; ts: number; type: string; detail: string | null }[] = [];

  async list(): Promise<ReleaseRow[]> {
    return [...this.releases.values()].map((row) => ({ ...row }));
  }

  async get(versionCode: number): Promise<ReleaseRow | null> {
    const row = this.releases.get(versionCode);
    return row ? { ...row } : null;
  }

  async insert(row: ReleaseRow): Promise<void> {
    this.releases.set(row.version_code, { ...row });
  }

  async update(versionCode: number, patch: Partial<ReleaseRow>): Promise<void> {
    const row = this.releases.get(versionCode);
    if (!row) return;
    this.releases.set(versionCode, { ...row, ...patch });
  }

  async remove(versionCode: number): Promise<void> {
    this.releases.delete(versionCode);
  }

  async putChunk(
    versionCode: number,
    index: number,
    sha256: string,
    data: ArrayBuffer,
  ): Promise<void> {
    this.chunks.set(`${versionCode}:${index}`, { sha256, data });
  }

  async storedIndexes(versionCode: number): Promise<number[]> {
    const prefix = `${versionCode}:`;
    return [...this.chunks.keys()]
      .filter((key) => key.startsWith(prefix))
      .map((key) => Number(key.slice(prefix.length)))
      .sort((a, b) => a - b);
  }

  async chunk(versionCode: number, index: number): Promise<StoredChunk | null> {
    const found = this.chunks.get(`${versionCode}:${index}`);
    return found ? { ...found } : null;
  }

  async deleteChunks(versionCode: number): Promise<void> {
    const prefix = `${versionCode}:`;
    for (const key of [...this.chunks.keys()]) {
      if (key.startsWith(prefix)) this.chunks.delete(key);
    }
  }

  async addEvent(
    versionCode: number,
    ts: number,
    type: string,
    detail: string | null,
  ): Promise<void> {
    this.log.push({ version_code: versionCode, ts, type, detail });
  }

  async events(limit: number) {
    return this.log.slice(-limit).reverse();
  }
}

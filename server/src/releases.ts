
/**
 * Released APKs, stored in D1 as 512 KB chunks so no request ever streams or
 * hashes a whole file inside a Worker's CPU budget.
 */
export const CHUNK_SIZE = 512 * 1024;
export const KEEP_RELEASES = 3;
export const MAX_CHUNKS = 4096;

export type ReleaseStatus = "draft" | "published" | "unpublished";

export interface ReleaseRow {
  version_code: number;
  version_name: string;
  notes: string;
  sha256: string;
  size_bytes: number;
  chunk_count: number;
  chunk_size: number;
  mandatory: number;
  status: ReleaseStatus;
  created_at: number;
  published_at: number | null;
}

export interface StoredChunk {
  sha256: string;
  data: ArrayBuffer;
}

export interface ReleaseStore {
  list(): Promise<ReleaseRow[]>;
  get(versionCode: number): Promise<ReleaseRow | null>;
  insert(row: ReleaseRow): Promise<void>;
  update(versionCode: number, patch: Partial<ReleaseRow>): Promise<void>;
  remove(versionCode: number): Promise<void>;
  putChunk(versionCode: number, index: number, sha256: string, data: ArrayBuffer): Promise<void>;
  storedIndexes(versionCode: number): Promise<number[]>;
  chunk(versionCode: number, index: number): Promise<StoredChunk | null>;
  deleteChunks(versionCode: number): Promise<void>;
  addEvent(versionCode: number, ts: number, type: string, detail: string | null): Promise<void>;
  events(limit: number): Promise<{ version_code: number; ts: number; type: string; detail: string | null }[]>;
}

export class ReleaseError extends Error {
  readonly status: number;
  readonly code: string;

  constructor(status: number, code: string, message: string) {
    super(message);
    this.status = status;
    this.code = code;
  }
}

/** Native WebCrypto digest, hex encoded. */
export async function sha256Hex(data: ArrayBuffer | Uint8Array): Promise<string> {
  const bytes = data instanceof Uint8Array ? data : new Uint8Array(data);
  const digest = new Uint8Array(await crypto.subtle.digest("SHA-256", bytes));
  return [...digest].map((byte) => byte.toString(16).padStart(2, "0")).join("");
}

export function chunkCountFor(sizeBytes: number): number {
  if (!Number.isFinite(sizeBytes) || sizeBytes <= 0) return 0;
  return Math.ceil(sizeBytes / CHUNK_SIZE);
}

function cleanNotes(value: unknown): string {
  return typeof value === "string" ? value.slice(0, 2000) : "";
}

function isHex64(value: unknown): value is string {
  return typeof value === "string" && /^[0-9a-f]{64}$/.test(value);
}

export async function listReleases(store: ReleaseStore): Promise<ReleaseRow[]> {
  const rows = await store.list();
  return rows.sort((a, b) => b.version_code - a.version_code);
}

export async function highestPublished(store: ReleaseStore): Promise<ReleaseRow | null> {
  const rows = (await store.list()).filter((row) => row.status === "published");
  rows.sort((a, b) => b.version_code - a.version_code);
  return rows[0] ?? null;
}

export async function latestPublished(
  store: ReleaseStore,
): Promise<ReleaseRow | null> {
  const published = await highestPublished(store);
  if (!published) return null;
  const rows = await store.list();
  const mandatory = rows
    .filter((row) => row.status === "published" && row.mandatory === 1)
    .sort((a, b) => b.version_code - a.version_code)[0];
  return mandatory && mandatory.version_code > published.version_code ? mandatory : published;
}

/** `min_version_code` is a server setting, raised when a release is mandatory. */
export function minVersionCodeFor(
  rows: ReleaseRow[],
): number {
  const mandatory = rows
    .filter((row) => row.status === "published" && row.mandatory === 1)
    .sort((a, b) => b.version_code - a.version_code)[0];
  return mandatory ? mandatory.version_code : 0;
}

export async function createDraft(
  store: ReleaseStore,
  input: {
    versionCode: number;
    versionName: string;
    notes: string;
    sha256: string;
    sizeBytes: number;
    mandatory: boolean;
  },
  now: number,
): Promise<ReleaseRow> {
  const versionCode = Math.trunc(Number(input.versionCode));
  if (!Number.isInteger(versionCode) || versionCode <= 0) {
    throw new ReleaseError(400, "BAD_VERSION_CODE", "versionCode must be a positive whole number.");
  }
  if (!input.versionName || input.versionName.length > 40) {
    throw new ReleaseError(400, "BAD_VERSION_NAME", "A version name is required (max 40 characters).");
  }
  if (!isHex64(input.sha256)) {
    throw new ReleaseError(400, "BAD_SHA256", "sha256 must be 64 lowercase hex characters.");
  }
  const sizeBytes = Math.trunc(Number(input.sizeBytes));
  if (!Number.isInteger(sizeBytes) || sizeBytes <= 0) {
    throw new ReleaseError(400, "BAD_SIZE", "sizeBytes must be a positive whole number.");
  }
  const chunkCount = chunkCountFor(sizeBytes);
  if (chunkCount > MAX_CHUNKS) {
    throw new ReleaseError(400, "TOO_LARGE", "That APK is too large to store.");
  }
  const rows = await store.list();
  const published = rows
    .filter((row) => row.status === "published")
    .sort((a, b) => b.version_code - a.version_code)[0];
  if (published && versionCode <= published.version_code) {
    throw new ReleaseError(
      409,
      "VERSION_NOT_NEWER",
      `versionCode ${versionCode} must be higher than the published ${published.version_code}.`,
    );
  }
  const existing = await store.get(versionCode);
  if (existing) {
    throw new ReleaseError(409, "ALREADY_EXISTS", `A release for versionCode ${versionCode} already exists.`);
  }
  const row: ReleaseRow = {
    version_code: versionCode,
    version_name: input.versionName.trim(),
    notes: cleanNotes(input.notes),
    sha256: input.sha256,
    size_bytes: sizeBytes,
    chunk_count: chunkCount,
    chunk_size: CHUNK_SIZE,
    mandatory: input.mandatory ? 1 : 0,
    status: "draft",
    created_at: now,
    published_at: null,
  };
  await store.insert(row);
  await store.addEvent(versionCode, now, "draft_created", input.versionName);
  return row;
}

export async function uploadChunk(
  store: ReleaseStore,
  versionCode: number,
  index: number,
  chunkSha256: string,
  data: ArrayBuffer,
  now: number,
): Promise<{ index: number; stored: number; chunkCount: number }> {
  const row = await store.get(versionCode);
  if (!row) {
    throw new ReleaseError(404, "NO_RELEASE", "No draft with that versionCode.");
  }
  if (row.status === "published") {
    throw new ReleaseError(409, "ALREADY_PUBLISHED", "Published releases cannot receive chunks.");
  }
  if (!Number.isInteger(index) || index < 0 || index >= row.chunk_count) {
    throw new ReleaseError(400, "BAD_INDEX", `Chunk index must be 0..${row.chunk_count - 1}.`);
  }
  if (!isHex64(chunkSha256)) {
    throw new ReleaseError(400, "BAD_CHUNK_SHA", "The chunk SHA-256 header is missing or malformed.");
  }
  if (data.byteLength === 0) {
    throw new ReleaseError(400, "EMPTY_CHUNK", "The chunk is empty.");
  }
  if (data.byteLength > CHUNK_SIZE) {
    throw new ReleaseError(400, "CHUNK_TOO_LARGE", "A chunk may be at most 512 KB.");
  }
  const actual = await sha256Hex(data);
  if (actual !== chunkSha256.toLowerCase()) {
    throw new ReleaseError(400, "CHUNK_HASH_MISMATCH", "That chunk does not match its SHA-256.");
  }
  await store.putChunk(versionCode, index, actual, data);
  const stored = (await store.storedIndexes(versionCode)).length;
  if (stored === 1) await store.addEvent(versionCode, now, "upload_started", null);
  return { index, stored, chunkCount: row.chunk_count };
}

export async function publishRelease(
  store: ReleaseStore,
  versionCode: number,
  now: number,
  keep: number = KEEP_RELEASES,
): Promise<ReleaseRow> {
  const row = await store.get(versionCode);
  if (!row) throw new ReleaseError(404, "NO_RELEASE", "No release with that versionCode.");
  if (row.status === "published") return row;
  const indexes = await store.storedIndexes(versionCode);
  const missing: number[] = [];
  for (let index = 0; index < row.chunk_count; index += 1) {
    if (!indexes.includes(index)) missing.push(index);
  }
  if (row.chunk_count === 0 || missing.length > 0) {
    throw new ReleaseError(
      409,
      "CHUNKS_MISSING",
      `Upload every chunk first. Missing: ${missing.slice(0, 10).join(", ")}${missing.length > 10 ? "…" : ""}.`,
    );
  }
  await store.update(versionCode, { status: "published", published_at: now });
  await store.addEvent(versionCode, now, "published", row.version_name);
  await pruneOldReleases(store, keep, now);
  return (await store.get(versionCode)) ?? { ...row, status: "published", published_at: now };
}

/** Keeps the newest few published releases, plus every draft. */
export async function pruneOldReleases(
  store: ReleaseStore,
  keep: number,
  now: number,
): Promise<number[]> {
  const rows = (await store.list()).filter((row) => row.status === "published");
  rows.sort((a, b) => b.version_code - a.version_code);
  const removed: number[] = [];
  for (const row of rows.slice(Math.max(0, keep))) {
    await store.deleteChunks(row.version_code);
    await store.remove(row.version_code);
    await store.addEvent(row.version_code, now, "pruned", "Kept the newest releases only.");
    removed.push(row.version_code);
  }
  return removed;
}

export async function unpublishRelease(
  store: ReleaseStore,
  versionCode: number,
  now: number,
): Promise<ReleaseRow> {
  const row = await store.get(versionCode);
  if (!row) throw new ReleaseError(404, "NO_RELEASE", "No release with that versionCode.");
  if (row.status !== "published") {
    throw new ReleaseError(409, "NOT_PUBLISHED", "That release is not published.");
  }
  await store.update(versionCode, { status: "unpublished", mandatory: 0 });
  await store.addEvent(versionCode, now, "unpublished", null);
  return (await store.get(versionCode))!;
}

export async function deleteRelease(
  store: ReleaseStore,
  versionCode: number,
  now: number,
): Promise<void> {
  const row = await store.get(versionCode);
  if (!row) throw new ReleaseError(404, "NO_RELEASE", "No release with that versionCode.");
  if (row.status === "published") {
    throw new ReleaseError(409, "PUBLISHED", "Unpublish the release before deleting it.");
  }
  await store.deleteChunks(versionCode);
  await store.remove(versionCode);
  await store.addEvent(versionCode, now, "deleted", null);
}

/** The signed body of `GET /v1/latest`. Carries nothing identifying. */
export async function releasePayload(store: ReleaseStore, now: number) {
  const rows = await store.list();
  const release = await latestPublished(store);
  if (!release) return { none: true, serverTime: now };
  const indexes = await store.storedIndexes(release.version_code);
  const chunkShas: string[] = [];
  for (let index = 0; index < release.chunk_count; index += 1) {
    const chunk = indexes.includes(index)
      ? await store.chunk(release.version_code, index)
      : null;
    chunkShas.push(chunk?.sha256 ?? "");
  }
  return {
    versionCode: release.version_code,
    versionName: release.version_name,
    minVersionCode: minVersionCodeFor(rows),
    sha256: release.sha256,
    sizeBytes: release.size_bytes,
    notes: release.notes,
    publishedAt: release.published_at,
    downloadPath: `/v1/download/${release.version_code}`,
    chunkCount: release.chunk_count,
    chunkSize: release.chunk_size,
    chunkShas,
    serverTime: now,
  };
}

/** Adoption: how many licenses last reported each version. */
export async function adoptionCounts(
  licenses: { app_version: string | null; status: string }[],
): Promise<{ total: number; byVersion: Record<string, number> }> {
  const byVersion: Record<string, number> = {};
  let total = 0;
  for (const row of licenses) {
    if (row.status === "cancelled") continue;
    total += 1;
    const key = row.app_version ?? "unknown";
    byVersion[key] = (byVersion[key] ?? 0) + 1;
  }
  return { total, byVersion };
}

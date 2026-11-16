import type { Db } from "./db.ts";
import {
  adoptionCounts,
  createDraft,
  deleteRelease,
  latestPublished,
  listReleases,
  minVersionCodeFor,
  publishRelease,
  releasePayload,
  unpublishRelease,
  uploadChunk,
  ReleaseError,
} from "./releases.ts";
import type { ReleaseStore } from "./releases.ts";
import { signPayload } from "./signing.ts";

export interface ReleaseDeps {
  db: Db;
  releases: ReleaseStore;
  signingKey: CryptoKey | null;
  now: () => number;
}

function json(body: unknown, status = 200): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: {
      "content-type": "application/json; charset=utf-8",
      "cache-control": "no-store",
    },
  });
}

function toError(error: unknown): Response {
  if (error instanceof ReleaseError) {
    return json({ error: error.code, message: error.message }, error.status);
  }
  const message = error instanceof Error ? error.message : "unexpected error";
  return json({ error: "SERVER_ERROR", message }, 500);
}

export async function handleLatest(deps: ReleaseDeps): Promise<Response> {
  if (!deps.signingKey) {
    return json(
      { error: "NOT_CONFIGURED", message: "The signing key is missing on the server." },
      503,
    );
  }
  try {
    const payload = await releasePayload(deps.releases, deps.now());
    const signed = await signPayload(deps.signingKey, payload);
    return new Response(JSON.stringify(signed), {
      status: 200,
      headers: {
        "content-type": "application/json; charset=utf-8",
        "cache-control": "no-store",
      },
    });
  } catch (error) {
    return toError(error);
  }
}

export async function handleDownload(
  deps: ReleaseDeps,
  versionCode: number,
  index: number,
): Promise<Response> {
  try {
    const release = await deps.releases.get(versionCode);
    if (!release || release.status !== "published") {
      return json({ error: "NOT_FOUND", message: "That version cannot be downloaded." }, 404);
    }
    if (!Number.isInteger(index) || index < 0 || index >= release.chunk_count) {
      return json({ error: "BAD_INDEX", message: "No such chunk." }, 400);
    }
    const chunk = await deps.releases.chunk(versionCode, index);
    if (!chunk) {
      return json({ error: "NOT_FOUND", message: "That chunk is missing." }, 404);
    }
    return new Response(chunk.data, {
      status: 200,
      headers: {
        "content-type": "application/octet-stream",
        "content-length": String(chunk.data.byteLength),
        "x-chunk-sha256": chunk.sha256,
        "cache-control": "public, max-age=31536000, immutable",
      },
    });
  } catch (error) {
    return toError(error);
  }
}

export async function handleAdminReleases(
  deps: ReleaseDeps,
  path: string,
  method: string,
  request: Request,
): Promise<Response> {
  const now = deps.now();
  try {
    if (path === "/admin/releases" && method === "GET") {
      const rows = await listReleases(deps.releases);
      const licenses = await deps.db.list({});
      const adoption = await adoptionCounts(
        licenses.map((row) => ({ app_version: row.app_version, status: row.status })),
      );
      return json({
        releases: rows.map((row) => ({
          versionCode: row.version_code,
          versionName: row.version_name,
          notes: row.notes,
          sha256: row.sha256,
          sizeBytes: row.size_bytes,
          status: row.status,
          mandatory: row.mandatory === 1,
          createdAt: row.created_at,
          publishedAt: row.published_at,
          onVersion: adoption.byVersion[String(row.version_code)] ?? 0,
        })),
        adoption,
        minVersionCode: minVersionCodeFor(rows),
        latest: (await latestPublished(deps.releases))?.version_code ?? null,
        serverTime: now,
      });
    }

    if (path === "/admin/releases" && method === "POST") {
      const body = (await request.json()) as Record<string, unknown>;
      const row = await createDraft(
        deps.releases,
        {
          versionCode: Number(body.versionCode),
          versionName: String(body.versionName ?? ""),
          notes: typeof body.notes === "string" ? body.notes : "",
          sha256: String(body.sha256 ?? "").toLowerCase(),
          sizeBytes: Number(body.sizeBytes),
          mandatory: body.mandatory === true,
        },
        now,
      );
      return json(
        {
          versionCode: row.version_code,
          chunkCount: row.chunk_count,
          chunkSize: row.chunk_size,
        },
        201,
      );
    }

    const chunkMatch = path.match(/^\/admin\/releases\/(\d+)\/chunk\/(\d+)$/);
    if (chunkMatch && method === "PUT") {
      const versionCode = Number(chunkMatch[1]);
      const index = Number(chunkMatch[2]);
      const sha = request.headers.get("x-chunk-sha256") ?? "";
      const data = await request.arrayBuffer();
      const result = await uploadChunk(deps.releases, versionCode, index, sha, data, now);
      return json(result);
    }

    const publishMatch = path.match(/^\/admin\/releases\/(\d+)\/(publish|unpublish)$/);
    if (publishMatch && method === "POST") {
      const versionCode = Number(publishMatch[1]);
      if (publishMatch[2] === "publish") {
        const row = await publishRelease(deps.releases, versionCode, now);
        return json({
          versionCode: row.version_code,
          status: row.status,
          publishedAt: row.published_at,
        });
      }
      const row = await unpublishRelease(deps.releases, versionCode, now);
      return json({ versionCode: row.version_code, status: row.status });
    }

    const deleteMatch = path.match(/^\/admin\/releases\/(\d+)$/);
    if (deleteMatch && method === "DELETE") {
      await deleteRelease(deps.releases, Number(deleteMatch[1]), now);
      return json({ deleted: Number(deleteMatch[1]) });
    }

    return json({ error: "NOT_FOUND", message: "Unknown release endpoint." }, 404);
  } catch (error) {
    return toError(error);
  }
}

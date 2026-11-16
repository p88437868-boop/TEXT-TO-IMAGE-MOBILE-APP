import {
  addUsage,
  cancelLicense,
  deleteLicense,
  extendLicense,
  exportCsv,
  licenseDetail,
  listLicenses,
  movePhone,
  patchLicense,
  resetUsage,
  setBlocked,
  setQuota,
  stats,
} from "./admin.ts";
import { D1Db, type Db } from "./db.ts";
import { HttpError, verifyLicense, createLicense } from "./licenses.ts";
import { handleAdminReleases, handleDownload, handleLatest } from "./release-routes.ts";
import { D1ReleaseStore } from "./release-store.ts";
import type { ReleaseStore } from "./releases.ts";
import { importPrivateKeyPem, signPayload } from "./signing.ts";
import type { QuotaMode, VerifyRequest } from "./types.ts";
import { sameSecret } from "./util.ts";

export interface Env {
  DB: D1Database;
  ADMIN_TOKEN?: string;
  LICENSE_SIGNING_KEY?: string;
  QUOTA_TZ?: string;
}

export interface AppDeps {
  db: Db;
  releases: ReleaseStore;
  adminToken: string;
  quotaTz: string;
  now: () => number;
  signingKey: CryptoKey | null;
  clientIp: (request: Request) => string;
}

const MAX_FAILED_ATTEMPTS_PER_HOUR = 10;

function json(body: unknown, status = 200): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: {
      "content-type": "application/json; charset=utf-8",
      "cache-control": "no-store",
    },
  });
}

async function readJson(request: Request): Promise<Record<string, unknown>> {
  const text = await request.text();
  if (!text.trim()) return {};
  try {
    const parsed = JSON.parse(text);
    if (parsed === null || typeof parsed !== "object" || Array.isArray(parsed)) {
      throw new Error("not an object");
    }
    return parsed as Record<string, unknown>;
  } catch {
    throw new HttpError(400, "INVALID_JSON", "The request body must be a JSON object.");
  }
}

export function createApp(deps: AppDeps) {
  return async function handle(request: Request): Promise<Response> {
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

      // Public update endpoints: they carry no key and no device code, and they
      // stay open for unactivated phones (the app is locked without a key).
      const releaseDeps = {
        db: deps.db,
        releases: deps.releases,
        signingKey: deps.signingKey,
        now: deps.now,
      };
      if (path === "/v1/latest" && method === "GET") {
        return await handleLatest(releaseDeps);
      }
      const chunkDownload = path.match(/^\/v1\/download\/(\d+)\/(\d+)$/);
      if (chunkDownload && method === "GET") {
        return await handleDownload(
          releaseDeps,
          Number(chunkDownload[1]),
          Number(chunkDownload[2]),
        );
      }

      if (path === "/admin" || path.startsWith("/admin/")) {
        const auth = request.headers.get("authorization") ?? "";
        const token = auth.startsWith("Bearer ") ? auth.slice(7).trim() : "";
        if (!deps.adminToken || !sameSecret(token, deps.adminToken)) {
          return json({ error: "UNAUTHORIZED", message: "Admin token missing or wrong." }, 401);
        }
        if (path.startsWith("/admin/releases")) {
          return await handleAdminReleases(
            { db: deps.db, releases: deps.releases, signingKey: deps.signingKey, now: deps.now },
            path,
            method,
            request,
          );
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
  };
}

async function handleVerify(deps: AppDeps, request: Request): Promise<Response> {
  if (!deps.signingKey) {
    return json(
      {
        error: "NOT_CONFIGURED",
        message: "The SIGNING key secret is missing on the server.",
      },
      503,
    );
  }

  const body = await readJson(request);
  const nonce = typeof body.nonce === "string" ? body.nonce.slice(0, 64) : "";
  if (!nonce) {
    return json({ error: "NONCE_REQUIRED", message: "A nonce is required." }, 400);
  }

  const verifyRequest: VerifyRequest = {
    key: typeof body.key === "string" ? body.key : "",
    deviceCode: typeof body.deviceCode === "string" ? body.deviceCode : "",
    deviceModel: typeof body.deviceModel === "string" ? body.deviceModel.slice(0, 80) : null,
    androidVersion:
      typeof body.androidVersion === "string" ? body.androidVersion.slice(0, 32) : null,
    appVersion: typeof body.appVersion === "string" ? body.appVersion.slice(0, 32) : null,
    nonce,
    usageCount: Number(body.usageCount ?? 0) || 0,
    reportId: typeof body.reportId === "string" ? body.reportId.slice(0, 64) : null,
  };

  const now = deps.now();
  const quotaTz = (await deps.db.getSetting("quota_tz")) ?? deps.quotaTz;
  const outcome = await verifyLicense({ db: deps.db, now, quotaTz }, verifyRequest);

  if (outcome.failedAttempt) {
    const hour = 3_600_000;
    const bucket = `${deps.clientIp(request)}|${verifyRequest.deviceCode}|${Math.floor(now / hour)}`;
    const count = await deps.db.bumpRate(bucket, (Math.floor(now / hour) + 1) * hour);
    if (count === 1) await deps.db.pruneRates(now);
    if (count > MAX_FAILED_ATTEMPTS_PER_HOUR) {
      return json(
        {
          error: "RATE_LIMITED",
          message: "Too many activation attempts. Try again in an hour.",
        },
        429,
      );
    }
  }

  return json(await signPayload(deps.signingKey, outcome.payload));
}

async function handleAdmin(
  deps: AppDeps,
  path: string,
  method: string,
  request: Request,
): Promise<Response> {
  const now = deps.now();
  const quotaTz = (await deps.db.getSetting("quota_tz")) ?? deps.quotaTz;
  const settings = { db: deps.db, now, quotaTz };

  if (path === "/admin/licenses" && method === "GET") {
    const url = new URL(request.url);
    const items = await listLicenses(
      settings,
      url.searchParams.get("search") ?? "",
      url.searchParams.get("status") ?? "",
    );
    return json({ licenses: items, serverTime: now });
  }

  if (path === "/admin/licenses" && method === "POST") {
    const body = await readJson(request);
    const quotaMode =
      body.quotaMode === "daily" || body.quotaMode === "total" ? body.quotaMode : "unlimited";
    const created = await createLicense(settings, {
      name: String(body.name ?? ""),
      deviceCode: String(body.deviceCode ?? ""),
      durationDays:
        body.durationDays === null || body.durationDays === undefined
          ? null
          : Number(body.durationDays),
      note: typeof body.note === "string" ? body.note : null,
      quotaMode: quotaMode as QuotaMode,
      quotaLimit: body.quotaLimit === null || body.quotaLimit === undefined
        ? null
        : Number(body.quotaLimit),
    });
    return json({ license: (await licenseDetail(settings, created.id)).license }, 201);
  }

  if (path === "/admin/stats" && method === "GET") {
    return json({ ...(await stats(settings)), serverTime: now });
  }

  if (path === "/admin/export.csv" && method === "GET") {
    return new Response(await exportCsv(settings), {
      headers: {
        "content-type": "text/csv; charset=utf-8",
        "content-disposition": 'attachment; filename="textpic-licenses.csv"',
      },
    });
  }

  if (path === "/admin/settings" && method === "GET") {
    return json({ quotaTz });
  }

  if (path === "/admin/settings" && method === "PUT") {
    const body = await readJson(request);
    const timeZone = String(body.quotaTz ?? "").trim();
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
    const body = await readJson(request);
    return json({ license: await patchLicense(settings, id, body) });
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

let cachedKeySource: string | null = null;
let cachedKey: CryptoKey | null = null;

async function signingKeyFrom(secret: string | undefined): Promise<CryptoKey | null> {
  if (!secret) return null;
  if (cachedKeySource === secret && cachedKey) return cachedKey;
  const imported = await importPrivateKeyPem(secret);
  cachedKeySource = secret;
  cachedKey = imported;
  return imported;
}

export default {
  async fetch(request: Request, env: Env): Promise<Response> {
    const signingKey = await signingKeyFrom(env.LICENSE_SIGNING_KEY);
    const deps: AppDeps = {
      db: new D1Db(env.DB),
      releases: new D1ReleaseStore(env.DB),
      adminToken: env.ADMIN_TOKEN ?? "",
      quotaTz: env.QUOTA_TZ ?? "UTC",
      now: () => Date.now(),
      signingKey,
      clientIp: (req) =>
        req.headers.get("cf-connecting-ip") ??
        req.headers.get("x-forwarded-for")?.split(",")[0].trim() ??
        "local",
    };
    return createApp(deps)(request);
  },
};

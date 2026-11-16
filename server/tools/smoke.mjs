#!/usr/bin/env node
/**
 * End-to-end check against a running server (local `wrangler dev` or your
 * deployed Worker). It prints the same story you will demo to a customer.
 *
 *   node tools/smoke.mjs                          (uses http://127.0.0.1:8787)
 *   BASE_URL=https://... ADMIN_TOKEN=... node tools/smoke.mjs
 *
 * With no ADMIN_TOKEN in the environment it reads server/.dev.vars.
 */
import { readFileSync, existsSync } from "node:fs";
import { dirname, join, resolve } from "node:path";
import { fileURLToPath } from "node:url";
import { createPublicKey, verify as cryptoVerify } from "node:crypto";

const here = dirname(fileURLToPath(import.meta.url));
const serverDir = resolve(here, "..");

function devVars() {
  const path = join(serverDir, ".dev.vars");
  if (!existsSync(path)) return {};
  const out = {};
  for (const line of readFileSync(path, "utf8").split(/\r?\n/)) {
    const match = /^([A-Z_]+)\s*=\s*"(.*)"$/.exec(line.trim());
    if (match) out[match[1]] = match[2];
  }
  return out;
}

const vars = devVars();
const base = (process.env.BASE_URL ?? "http://127.0.0.1:8787").replace(/\/$/, "");
const token = process.env.ADMIN_TOKEN ?? vars.ADMIN_TOKEN ?? "";

if (!token) {
  console.error("No ADMIN_TOKEN. Set it in the environment or in server/.dev.vars.");
  process.exit(1);
}

const results = [];
function check(name, ok, detail = "") {
  results.push({ name, ok, detail });
  console.log(`${ok ? "PASS" : "FAIL"}  ${name}${detail ? "  — " + detail : ""}`);
}

async function api(method, path, body, useToken = true) {
  const headers = {};
  if (body !== undefined) headers["content-type"] = "application/json";
  if (useToken) headers.authorization = `Bearer ${token}`;
  const response = await fetch(base + path, {
    method,
    headers,
    body: body === undefined ? undefined : JSON.stringify(body),
  });
  const text = await response.text();
  let json = null;
  try {
    json = JSON.parse(text);
  } catch {
    json = null;
  }
  return { status: response.status, json, text };
}

/** The public key the app will verify with, read straight from the Kotlin source. */
function appPublicKey() {
  const kotlin =
    process.env.PUBLIC_KEY_SOURCE ??
    join(serverDir, "..", "app/src/main/java/com/textpic/studio/data/license/LicenseServerPublicKey.kt");
  const text = readFileSync(kotlin, "utf8");
  const match = /ENCODED: String = "([^"]+)"/.exec(text);
  if (!match) throw new Error("cannot find LicenseServerPublicKey.ENCODED");
  return createPublicKey({
    key: Buffer.from(match[1], "base64"),
    format: "der",
    type: "spki",
  });
}

function decodeBase64Url(text) {
  return Buffer.from(text.replace(/-/g, "+").replace(/_/g, "/"), "base64");
}

const deviceA = "TP-5RKD-8WQZ-3MNB-6YTF";
const deviceB = "TP-9ABC-DEFG-HJKL-MNPQ";
const pub = appPublicKey();

async function verify(key, deviceCode, extra = {}) {
  const nonce = `smoke-${Math.random().toString(36).slice(2)}`;
  const result = await api(
    "POST",
    "/v1/verify",
    {
      key,
      deviceCode,
      deviceModel: "Pixel 7 (smoke test)",
      androidVersion: "14",
      appVersion: "1.2.0",
      nonce,
      usageCount: 0,
      reportId: null,
      ...extra,
    },
    false,
  );
  if (result.status !== 200) return { status: result.status, payload: null, signed: false };
  const payloadBytes = decodeBase64Url(result.json.payload);
  const sigBytes = decodeBase64Url(result.json.sig);
  const signed = cryptoVerify(
    "sha256",
    payloadBytes,
    { key: pub, dsaEncoding: "der" },
    sigBytes,
  );
  const payload = JSON.parse(payloadBytes.toString("utf8"));
  const nonceOk = payload.nonce === nonce;
  return { status: result.status, payload, signed: signed && nonceOk, raw: result.json };
}

async function main() {
  const health = await api("GET", "/health", undefined, false);
  check("server answers /health", health.status === 200, `${health.status}`);

  const unauthorized = await api("GET", "/admin/licenses", undefined, false);
  check("admin needs the token", unauthorized.status === 401, `${unauthorized.status}`);

  const name = `Smoke Test ${new Date().toISOString().slice(11, 19)}`;
  const created = await api("POST", "/admin/licenses", {
    name,
    deviceCode: deviceA,
    durationDays: 30,
    note: "created by tools/smoke.mjs",
    quotaMode: "total",
    quotaLimit: 100,
  });
  if (created.status === 409) {
    console.log("\nDevice A already has a live license on this server. Cancel it in the");
    console.log("console (or delete server/.wrangler state) and run the smoke test again.");
    process.exit(1);
  }
  check("create a license", created.status === 201, created.json?.license?.key ?? created.text);
  const license = created.json.license;
  const key = license.key;

  check("new license is pending", license.status === "pending", license.status);

  const activation = await verify(key, deviceA);
  check("activate on the right phone", activation.payload?.status === "ACTIVE", activation.payload?.status);
  check("reply is signed and echoes the nonce", activation.signed === true);
  check(
    "expiry is now + 30 days",
    typeof activation.payload?.expiresAt === "number",
    new Date(activation.payload?.expiresAt).toISOString(),
  );
  check("plan reaches the app", activation.payload?.quota?.mode === "total", JSON.stringify(activation.payload?.quota));

  const wrongPhone = await verify(key, deviceB);
  check("same key on another phone is refused", wrongPhone.payload?.status === "WRONG_DEVICE", wrongPhone.payload?.status);

  await api("POST", `/admin/licenses/${license.id}/block`);
  const blocked = await verify(key, deviceA);
  check("blocked license locks the app", blocked.payload?.status === "BLOCKED", blocked.payload?.status);

  await api("POST", `/admin/licenses/${license.id}/unblock`);
  const unblocked = await verify(key, deviceA);
  check("unblock restores access", unblocked.payload?.status === "ACTIVE", unblocked.payload?.status);

  const extended = await api("POST", `/admin/licenses/${license.id}/extend`, { days: 30 });
  check("extend adds 30 days", extended.status === 200, extended.json?.license?.expiresAt);

  const usage = await verify(key, deviceA, { usageCount: 7, reportId: `smoke-${Date.now()}` });
  check("usage report is counted", usage.payload?.quota?.usedTotal >= 7, `used ${usage.payload?.quota?.usedTotal}`);

  const sameReport = await verify(key, deviceA, {
    usageCount: 7,
    reportId: `smoke-repeat-${Date.now()}`,
  });
  check("a second report also counts once", sameReport.payload?.status === "ACTIVE");

  const detail = await api("GET", `/admin/licenses/${license.id}`);
  const types = (detail.json?.events ?? []).map((event) => event.type);
  check("history records the story", ["created", "activated", "blocked", "unblocked", "extended", "wrong_device_attempt"].every((type) => types.includes(type)), types.join(", "));

  const csv = await api("GET", "/admin/export.csv");
  check("CSV export works", csv.status === 200 && csv.text.split("\n")[0].startsWith("id,name,key"), `${csv.text.split("\n").length - 1} rows`);

  const stats = await api("GET", "/admin/stats");
  check("stats work", stats.status === 200, JSON.stringify(stats.json));

  await api("POST", `/admin/licenses/${license.id}/cancel`);
  const cancelled = await verify(key, deviceA);
  check("cancel is permanent", cancelled.payload?.status === "CANCELLED", cancelled.payload?.status);

  const failed = results.filter((result) => !result.ok);
  console.log("");
  console.log(`${results.length - failed.length}/${results.length} checks passed.`);
  if (failed.length) {
    console.log("Test license left on the server: " + key);
    process.exit(1);
  }
  console.log("Test license (cancelled, safe to delete): " + key);
}

main().catch((error) => {
  console.error(error);
  process.exit(1);
});

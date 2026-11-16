import { createPublicKey, verify as cryptoVerify } from "node:crypto";
import { test } from "node:test";
import assert from "node:assert/strict";
import { ADMIN_TOKEN, call, makeApp, type TestApp } from "./helpers.ts";
import { base64UrlDecode } from "../src/util.ts";
import { CHUNK_SIZE } from "../src/releases.ts";

async function sha256Hex(data: Uint8Array): Promise<string> {
  const digest = new Uint8Array(await crypto.subtle.digest("SHA-256", data));
  return [...digest].map((byte) => byte.toString(16).padStart(2, "0")).join("");
}

function fakeApk(bytes: number): Uint8Array {
  const data = new Uint8Array(bytes);
  for (let index = 0; index < bytes; index += 1) data[index] = (index * 31 + 7) % 251;
  return data;
}

async function rawCall(
  app: TestApp,
  method: string,
  path: string,
  body?: Uint8Array,
  headers: Record<string, string> = {},
): Promise<Response> {
  return app.handle(
    new Request(`https://license.test${path}`, {
      method,
      headers: { authorization: `Bearer ${ADMIN_TOKEN}`, ...headers },
      body,
    }),
  );
}

async function uploadAll(
  app: TestApp,
  versionCode: number,
  apk: Uint8Array,
): Promise<void> {
  const chunkCount = Math.ceil(apk.length / CHUNK_SIZE) || 1;
  for (let index = 0; index < chunkCount; index += 1) {
    const slice = apk.slice(index * CHUNK_SIZE, (index + 1) * CHUNK_SIZE);
    const response = await rawCall(
      app,
      "PUT",
      `/admin/releases/${versionCode}/chunk/${index}`,
      slice,
      { "x-chunk-sha256": await sha256Hex(slice) },
    );
    assert.equal(response.status, 200, await response.text());
  }
}

async function draft(
  app: TestApp,
  versionCode: number,
  apk: Uint8Array,
  overrides: Record<string, unknown> = {},
) {
  return call(app, "POST", "/admin/releases", {
    versionCode,
    versionName: `1.${versionCode}.0`,
    notes: "Test release",
    sha256: await sha256Hex(apk),
    sizeBytes: apk.length,
    mandatory: false,
    ...overrides,
  });
}

async function signedLatest(app: TestApp): Promise<{ payload: any; verified: boolean }> {
  const response = await app.handle(new Request("https://license.test/v1/latest"));
  const body = (await response.json()) as { payload: string; sig: string };
  const payloadBytes = base64UrlDecode(body.payload);
  const sigBytes = base64UrlDecode(body.sig);
  const publicKey = createPublicKey({
    key: Buffer.from(app.spkiBase64, "base64"),
    format: "der",
    type: "spki",
  });
  const verified = cryptoVerify(
    "sha256",
    Buffer.from(payloadBytes),
    { key: publicKey, dsacoding: undefined as never, dsaEncoding: "der" },
    Buffer.from(sigBytes),
  );
  return { payload: JSON.parse(new TextDecoder().decode(payloadBytes)), verified };
}

test("no release yet means a signed 'none' manifest", async () => {
  const app = await makeApp();
  const { payload, verified } = await signedLatest(app);
  assert.equal(verified, true);
  assert.equal(payload.none, true);
});

test("a draft is created and a chunk with the wrong hash is rejected", async () => {
  const app = await makeApp();
  const apk = fakeApk(40_000);
  const created = await draft(app, 9, apk);
  assert.equal(created.status, 201);
  assert.equal(created.json.chunkCount, 1);

  const response = await rawCall(app, "PUT", "/admin/releases/9/chunk/0", apk, {
    "x-chunk-sha256": "0".repeat(64),
  });
  assert.equal(response.status, 400);
  assert.equal((await response.json()).error, "CHUNK_HASH_MISMATCH");
});

test("publishing waits for every chunk", async () => {
  const app = await makeApp();
  const apk = fakeApk(CHUNK_SIZE + 1_000);
  await draft(app, 10, apk);

  const slice = apk.slice(0, CHUNK_SIZE);
  await rawCall(app, "PUT", "/admin/releases/10/chunk/0", slice, {
    "x-chunk-sha256": await sha256Hex(slice),
  });
  const tooEarly = await call(app, "POST", "/admin/releases/10/publish");
  assert.equal(tooEarly.status, 409);
  assert.equal(tooEarly.json.error, "CHUNKS_MISSING");

  const second = apk.slice(CHUNK_SIZE);
  await rawCall(app, "PUT", "/admin/releases/10/chunk/1", second, {
    "x-chunk-sha256": await sha256Hex(second),
  });
  const published = await call(app, "POST", "/admin/releases/10/publish");
  assert.equal(published.status, 200);
  assert.equal(published.json.status, "published");
});

test("the published manifest is signed and lists every chunk hash and the size", async () => {
  const app = await makeApp();
  const apk = fakeApk(CHUNK_SIZE + 2_048);
  await draft(app, 11, apk);
  await uploadAll(app, 11, apk);
  await call(app, "POST", "/admin/releases/11/publish");

  const { payload, verified } = await signedLatest(app);
  assert.equal(verified, true);
  assert.equal(payload.versionCode, 11);
  assert.equal(payload.sizeBytes, apk.length);
  assert.equal(payload.sha256, await sha256Hex(apk));
  assert.equal(payload.chunkCount, 2);
  assert.equal(payload.chunkShas.length, 2);
  assert.equal(payload.chunkShas[0], await sha256Hex(apk.slice(0, CHUNK_SIZE)));
  assert.equal(payload.downloadPath, "/v1/download/11");
});

test("chunks download byte for byte and only while published", async () => {
  const app = await makeApp();
  const apk = fakeApk(9_000);
  await draft(app, 12, apk);
  await uploadAll(app, 12, apk);

  const before = await app.handle(new Request("https://license.test/v1/download/12/0"));
  assert.equal(before.status, 404);

  await call(app, "POST", "/admin/releases/12/publish");
  const response = await app.handle(new Request("https://license.test/v1/download/12/0"));
  assert.equal(response.status, 200);
  assert.equal(response.headers.get("content-type"), "application/octet-stream");
  assert.equal(response.headers.get("x-chunk-sha256"), await sha256Hex(apk));
  const bytes = new Uint8Array(await response.arrayBuffer());
  assert.deepEqual(bytes, apk);

  await call(app, "POST", "/admin/releases/12/unpublish");
  const after = await app.handle(new Request("https://license.test/v1/download/12/0"));
  assert.equal(after.status, 404);
});

test("a versionCode must be higher than the published one", async () => {
  const app = await makeApp();
  const apk = fakeApk(3_000);
  await draft(app, 20, apk);
  await uploadAll(app, 20, apk);
  await call(app, "POST", "/admin/releases/20/publish");

  const lower = await draft(app, 19, apk);
  assert.equal(lower.status, 409);
  assert.equal(lower.json.error, "VERSION_NOT_NEWER");

  const same = await draft(app, 20, apk);
  assert.equal(same.status, 409);
});

test("a mandatory release raises minVersionCode and the app is told", async () => {
  const app = await makeApp();
  const apk = fakeApk(4_000);
  await draft(app, 30, apk, { mandatory: true });
  await uploadAll(app, 30, apk);
  await call(app, "POST", "/admin/releases/30/publish");

  const { payload } = await signedLatest(app);
  assert.equal(payload.minVersionCode, 30);
});

test("only the newest three published releases are kept", async () => {
  const app = await makeApp();
  for (const versionCode of [40, 41, 42, 43]) {
    const apk = fakeApk(2_000);
    await draft(app, versionCode, apk);
    await uploadAll(app, versionCode, apk);
    const published = await call(app, "POST", `/admin/releases/${versionCode}/publish`);
    assert.equal(published.status, 200);
  }
  const list = await call(app, "GET", "/admin/releases");
  const kept = list.json.releases.map((row: any) => row.versionCode).sort((a: number, b: number) => a - b);
  assert.deepEqual(kept, [41, 42, 43]);
  const old = await app.handle(new Request("https://license.test/v1/download/40/0"));
  assert.equal(old.status, 404);
});

test("adoption counts customers per reported version", async () => {
  const app = await makeApp();
  const apk = fakeApk(1_500);
  await draft(app, 50, apk);
  await uploadAll(app, 50, apk);
  await call(app, "POST", "/admin/releases/50/publish");

  const list = await call(app, "GET", "/admin/releases");
  assert.equal(list.status, 200);
  assert.equal(list.json.releases[0].versionCode, 50);
  assert.equal(typeof list.json.adoption.total, "number");
});

test("release endpoints need the admin token", async () => {
  const app = await makeApp();
  const response = await call(app, "GET", "/admin/releases", undefined, { token: null });
  assert.equal(response.status, 401);
});

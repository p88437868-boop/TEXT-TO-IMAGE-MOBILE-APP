import { createPublicKey, verify as cryptoVerify } from "node:crypto";
import { MemoryDb } from "../src/db-memory.ts";
import { createApp, type AppDeps } from "../src/index.ts";
import { MemoryReleaseStore } from "../src/release-store.ts";
import { importPrivateKeyPem } from "../src/signing.ts";
import { base64StdEncode, base64UrlDecode } from "../src/util.ts";

export const ADMIN_TOKEN = "test-admin-token";

export interface SigningKeys {
  pem: string;
  spkiBase64: string;
}

export function toPem(label: string, der: Uint8Array): string {
  const lines = base64StdEncode(der).match(/.{1,64}/g) ?? [];
  return `-----BEGIN ${label}-----\n${lines.join("\n")}\n-----END ${label}-----\n`;
}

export async function newSigningKeys(): Promise<SigningKeys> {
  const pair = await crypto.subtle.generateKey({ name: "ECDSA", namedCurve: "P-256" }, true, [
    "sign",
    "verify",
  ]);
  const pkcs8 = new Uint8Array(await crypto.subtle.exportKey("pkcs8", pair.privateKey));
  const spki = new Uint8Array(await crypto.subtle.exportKey("spki", pair.publicKey));
  return { pem: toPem("PRIVATE KEY", pkcs8), spkiBase64: base64StdEncode(spki) };
}

export interface TestApp {
  db: MemoryDb;
  releases: MemoryReleaseStore;
  handle: (request: Request) => Promise<Response>;
  spkiBase64: string;
  now: number;
  advance(ms: number): void;
  setNow(value: number): void;
}

export async function makeApp(start = Date.UTC(2026, 0, 15, 12, 0, 0)): Promise<TestApp> {
  const keys = await newSigningKeys();
  const db = new MemoryDb();
  const releases = new MemoryReleaseStore();
  const app: TestApp = {
    db,
    releases,
    spkiBase64: keys.spkiBase64,
    now: start,
    handle: async () => new Response("unused"),
    advance(ms: number) {
      app.now += ms;
    },
    setNow(value: number) {
      app.now = value;
    },
  };
  const deps: AppDeps = {
    db,
    releases,
    adminToken: ADMIN_TOKEN,
    quotaTz: "UTC",
    now: () => app.now,
    signingKey: await importPrivateKeyPem(keys.pem),
    clientIp: () => "203.0.113.7",
  };
  app.handle = createApp(deps);
  return app;
}

export interface CallResult {
  status: number;
  json: any;
  text: string;
}

export async function call(
  app: TestApp,
  method: string,
  path: string,
  body?: unknown,
  options: { token?: string | null } = {},
): Promise<CallResult> {
  const headers: Record<string, string> = {};
  if (body !== undefined) headers["content-type"] = "application/json";
  const token = options.token === null ? null : (options.token ?? ADMIN_TOKEN);
  if (token !== null) headers.authorization = `Bearer ${token}`;
  const response = await app.handle(
    new Request(`https://license.test${path}`, {
      method,
      headers,
      body: body === undefined ? undefined : JSON.stringify(body),
    }),
  );
  const text = await response.text();
  let json: any = null;
  try {
    json = JSON.parse(text);
  } catch {
    json = null;
  }
  return { status: response.status, json, text };
}

export const DEVICE_A = "TP-5RKD-8WQZ-3MNB-6YTF";
export const DEVICE_B = "TP-9ABC-DEFG-HJKL-MNPQ";

export interface VerifyResponse {
  status: number;
  payload: Record<string, any> | null;
  sig: string | null;
  raw: any;
  verified: boolean;
}

let nonceCounter = 0;
export function nextNonce(): string {
  nonceCounter++;
  return `nonce-${nonceCounter}-${Math.random().toString(36).slice(2, 10)}`;
}

export async function verifyCall(
  app: TestApp,
  input: {
    key: string;
    deviceCode?: string;
    usageCount?: number;
    reportId?: string | null;
    nonce?: string;
    deviceModel?: string;
  },
): Promise<VerifyResponse> {
  const nonce = input.nonce ?? nextNonce();
  const result = await call(app, "POST", "/v1/verify", {
    key: input.key,
    deviceCode: input.deviceCode ?? DEVICE_A,
    deviceModel: input.deviceModel ?? "Pixel 7",
    androidVersion: "14",
    appVersion: "1.2.0",
    nonce,
    usageCount: input.usageCount ?? 0,
    reportId: input.reportId ?? null,
  });
  if (result.status !== 200 || !result.json?.payload) {
    return { ...result, payload: null, sig: null, verified: false };
  }
  const payloadBytes = base64UrlDecode(result.json.payload);
  const sigBytes = base64UrlDecode(result.json.sig);
  const publicKey = createPublicKey({
    key: Buffer.from(app.spkiBase64, "base64"),
    format: "der",
    type: "spki",
  });
  const verified = cryptoVerify(
    "sha256",
    Buffer.from(payloadBytes),
    { key: publicKey, dsaEncoding: "der" },
    Buffer.from(sigBytes),
  );
  const payload = JSON.parse(new TextDecoder().decode(payloadBytes));
  return { ...result, payload, sig: result.json.sig, verified };
}

export async function createCustomer(
  app: TestApp,
  overrides: Record<string, unknown> = {},
): Promise<any> {
  const body = {
    name: "Amina Khan",
    deviceCode: DEVICE_A,
    durationDays: 30,
    note: null,
    quotaMode: "unlimited",
    quotaLimit: null,
    ...overrides,
  };
  const result = await call(app, "POST", "/admin/licenses", body);
  if (result.status !== 201) throw new Error(`create failed: ${result.status} ${result.text}`);
  return result.json.license;
}

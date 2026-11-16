import assert from "node:assert/strict";
import test from "node:test";
import {
  base64UrlDecode,
  base64UrlEncode,
  dayKey,
  generateLicenseKey,
  nextMidnight,
  normalizeDeviceCode,
  normalizeLicenseKey,
  rawEcdsaToDer,
  sameSecret,
} from "../src/util.ts";
import { toPem, newSigningKeys } from "./helpers.ts";
import { importPrivateKeyPem } from "../src/signing.ts";
import { createPublicKey, verify as cryptoVerify } from "node:crypto";

test("base64url round trip", () => {
  const bytes = new Uint8Array([0, 1, 2, 250, 251, 252, 253, 254, 255, 65]);
  const encoded = base64UrlEncode(bytes);
  assert.doesNotMatch(encoded, /[+/=]/);
  assert.deepEqual([...base64UrlDecode(encoded)], [...bytes]);
});

test("generated keys use the safe alphabet and groups of four", () => {
  for (let i = 0; i < 50; i++) {
    const key = generateLicenseKey();
    assert.match(key, /^[2-9A-HJ-NP-Z]{4}(-[2-9A-HJ-NP-Z]{4}){3}$/);
    assert.equal(key.replace(/-/g, "").length, 16);
  }
});

test("license keys and device codes are normalised from pasted text", () => {
  assert.equal(normalizeLicenseKey("abcd efgh jklm npqr"), "ABCD-EFGH-JKLM-NPQR");
  assert.equal(normalizeLicenseKey("ABCD-EFGH-JKLM-NPQ"), null);
  assert.equal(normalizeLicenseKey("ABCD-EFGH-JKLM-NPQO"), null, "O is not in the alphabet");
  assert.equal(normalizeDeviceCode("tp 5rkd 8wqz 3mnb 6ytf"), "TP-5RKD-8WQZ-3MNB-6YTF");
  assert.equal(normalizeDeviceCode("5RKD8WQZ3MNB6YTF"), "TP-5RKD-8WQZ-3MNB-6YTF");
  assert.equal(normalizeDeviceCode("TP-1234"), null);
});

test("secrets are compared exactly", () => {
  assert.equal(sameSecret("abc", "abc"), true);
  assert.equal(sameSecret("abc", "abd"), false);
  assert.equal(sameSecret("abc", "abcd"), false);
  assert.equal(sameSecret("", ""), true);
});

test("UTC day boundaries are midnight UTC", () => {
  const noon = Date.UTC(2026, 0, 15, 12, 0, 0);
  assert.equal(dayKey(noon, "UTC"), "2026-01-15");
  assert.equal(nextMidnight(noon, "UTC"), Date.UTC(2026, 0, 16));
});

test("day boundaries follow the local time zone", () => {
  const instant = Date.UTC(2026, 0, 15, 20, 0, 0); // 01:30 on the 16th in Kolkata
  assert.equal(dayKey(instant, "Asia/Kolkata"), "2026-01-16");
  assert.equal(nextMidnight(instant, "Asia/Kolkata"), Date.UTC(2026, 0, 16, 18, 30));
  assert.equal(dayKey(instant, "America/New_York"), "2026-01-15");
});

test("day boundaries survive a daylight saving change", () => {
  // Paris switches to summer time on 29 March 2026 at 02:00.
  const beforeSwitch = Date.UTC(2026, 2, 28, 23, 30, 0); // 00:30 CET on the 29th
  assert.equal(dayKey(beforeSwitch, "Europe/Paris"), "2026-03-29");
  assert.equal(
    nextMidnight(beforeSwitch, "Europe/Paris"),
    Date.UTC(2026, 2, 29, 22, 0, 0),
    "midnight after the switch is 22:00 UTC",
  );

  // 14:00 CEST on the 29th: the next midnight is 00:00 CEST on the 30th = 22:00 UTC.
  const afterSwitch = Date.UTC(2026, 2, 29, 12, 0, 0);
  assert.equal(dayKey(afterSwitch, "Europe/Paris"), "2026-03-29");
  assert.equal(nextMidnight(afterSwitch, "Europe/Paris"), Date.UTC(2026, 2, 29, 22, 0, 0));
  assert.equal(
    nextMidnight(Date.UTC(2026, 2, 30, 12, 0, 0), "Europe/Paris"),
    Date.UTC(2026, 2, 30, 22, 0, 0),
  );
});

test("DER signatures are accepted by standard ECDSA verifiers (Java uses the same)", async () => {
  const keys = await newSigningKeys();
  const privateKey = await importPrivateKeyPem(keys.pem);
  const message = new TextEncoder().encode("hello activation");
  const raw = new Uint8Array(
    await crypto.subtle.sign({ name: "ECDSA", hash: "SHA-256" }, privateKey, message),
  );
  assert.equal(raw.length, 64);

  const publicKey = createPublicKey({
    key: Buffer.from(keys.spkiBase64, "base64"),
    format: "der",
    type: "spki",
  });
  assert.equal(
    cryptoVerify(
      "sha256",
      Buffer.from(message),
      { key: publicKey, dsaEncoding: "der" },
      Buffer.from(rawEcdsaToDer(raw)),
    ),
    true,
  );

  const tampered = new TextEncoder().encode("hello activation!");
  assert.equal(
    cryptoVerify(
      "sha256",
      Buffer.from(tampered),
      { key: publicKey, dsaEncoding: "der" },
      Buffer.from(rawEcdsaToDer(raw)),
    ),
    false,
  );
});

test("PEM helpers round trip a private key", async () => {
  const keys = await newSigningKeys();
  assert.match(keys.pem, /^-----BEGIN PRIVATE KEY-----/);
  const reimported = await importPrivateKeyPem(keys.pem);
  assert.equal(reimported.type, "private");
  const shortPem = toPem("PRIVATE KEY", base64UrlDecode(base64UrlEncode(new Uint8Array([1, 2, 3]))));
  await assert.rejects(() => importPrivateKeyPem(shortPem));
});

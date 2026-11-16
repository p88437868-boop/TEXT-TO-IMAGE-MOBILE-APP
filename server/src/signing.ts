import type { VerifyPayload } from "./types.ts";
import { base64UrlDecode, base64UrlEncode, rawEcdsaToDer, utf8 } from "./util.ts";

const ALGORITHM: EcKeyImportParams = { name: "ECDSA", namedCurve: "P-256" };

/** Imports the PKCS#8 PEM private key stored as the `LICENSE_SIGNING_KEY` secret. */
export async function importPrivateKeyPem(pem: string): Promise<CryptoKey> {
  const base64 = pem
    .replace(/-----BEGIN [^-]+-----/g, "")
    .replace(/-----END [^-]+-----/g, "")
    .replace(/\s+/g, "");
  if (!base64) throw new Error("empty signing key");
  const der = base64UrlDecode(base64);
  return crypto.subtle.importKey("pkcs8", der, ALGORITHM, false, ["sign"]);
}

/**
 * Signs a reply. `payload` is sent base64url-encoded and the signature is
 * DER-encoded (what Java's `SHA256withECDSA` expects), base64url-encoded.
 */
export async function signPayload(
  key: CryptoKey,
  // A license reply or an update manifest: both are just signed JSON objects.
  payload: VerifyPayload | object,
): Promise<{ payload: string; sig: string }> {
  const bytes = utf8(JSON.stringify(payload));
  const raw = new Uint8Array(
    await crypto.subtle.sign({ name: "ECDSA", hash: "SHA-256" }, key, bytes),
  );
  return { payload: base64UrlEncode(bytes), sig: base64UrlEncode(rawEcdsaToDer(raw)) };
}

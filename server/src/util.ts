import {
  DEVICE_CODE_PATTERN,
  LICENSE_ALPHABET,
  LICENSE_KEY_PATTERN,
  type QuotaMode,
} from "./types.ts";

const BASE64 =
  "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/";

export function base64UrlEncode(bytes: Uint8Array): string {
  let out = "";
  for (let i = 0; i < bytes.length; i += 3) {
    const b0 = bytes[i];
    const b1 = bytes[i + 1];
    const b2 = bytes[i + 2];
    out += BASE64[b0 >> 2];
    out += BASE64[((b0 & 3) << 4) | ((b1 ?? 0) >> 4)];
    if (b1 === undefined) break;
    out += BASE64[((b1 & 15) << 2) | ((b2 ?? 0) >> 6)];
    if (b2 === undefined) break;
    out += BASE64[b2 & 63];
  }
  return out.replace(/\+/g, "-").replace(/\//g, "_").replace(/=+$/, "");
}

export function base64UrlDecode(text: string): Uint8Array {
  const clean = text.replace(/-/g, "+").replace(/_/g, "/").replace(/=+$/, "");
  const out = new Uint8Array(Math.floor((clean.length * 3) / 4));
  let buffer = 0;
  let bits = 0;
  let index = 0;
  for (const char of clean) {
    const value = BASE64.indexOf(char);
    if (value < 0) throw new Error("invalid base64url");
    buffer = (buffer << 6) | value;
    bits += 6;
    if (bits >= 8) {
      bits -= 8;
      out[index++] = (buffer >> bits) & 0xff;
    }
  }
  return out.subarray(0, index);
}

export function utf8(text: string): Uint8Array {
  return new TextEncoder().encode(text);
}

export function base64StdEncode(bytes: Uint8Array): string {
  let out = "";
  for (let i = 0; i < bytes.length; i += 3) {
    const b0 = bytes[i];
    const b1 = bytes[i + 1];
    const b2 = bytes[i + 2];
    out += BASE64[b0 >> 2];
    out += BASE64[((b0 & 3) << 4) | ((b1 ?? 0) >> 4)];
    out += b1 === undefined ? "=" : BASE64[((b1 & 15) << 2) | ((b2 ?? 0) >> 6)];
    out += b2 === undefined ? "=" : BASE64[b2 & 63];
  }
  return out;
}

function randomBytes(length: number): Uint8Array {
  const bytes = new Uint8Array(length);
  crypto.getRandomValues(bytes);
  return bytes;
}

export function randomId(): string {
  return Array.from(randomBytes(9), (b) => b.toString(16).padStart(2, "0")).join("");
}

/** 16 characters of the license alphabet (256 / 32 = 8, so no modulo bias). */
export function generateLicenseKey(): string {
  const bytes = randomBytes(16);
  let text = "";
  for (const byte of bytes) text += LICENSE_ALPHABET[byte % 32];
  return group(text);
}

export function group(text: string, size = 4): string {
  const parts: string[] = [];
  for (let i = 0; i < text.length; i += size) parts.push(text.slice(i, i + size));
  return parts.join("-");
}

export function normalizeLicenseKey(input: unknown): string | null {
  if (typeof input !== "string") return null;
  const clean = input.toUpperCase().replace(/[^0-9A-Z]/g, "");
  if (clean.length !== 16) return null;
  const grouped = group(clean);
  return LICENSE_KEY_PATTERN.test(grouped) ? grouped : null;
}

export function normalizeDeviceCode(input: unknown): string | null {
  if (typeof input !== "string") return null;
  const clean = input
    .toUpperCase()
    .replace(/[^0-9A-Z]/g, "")
    .replace(/^TP/, "");
  if (clean.length !== 16) return null;
  const formatted = `TP-${group(clean)}`;
  return DEVICE_CODE_PATTERN.test(formatted) ? formatted : null;
}

/** Length-independent-ish constant time comparison for secrets. */
export function sameSecret(a: string, b: string): boolean {
  const left = utf8(a);
  const right = utf8(b);
  let diff = left.length ^ right.length;
  const length = Math.max(left.length, right.length);
  for (let i = 0; i < length; i++) {
    diff |= (left[i] ?? 0) ^ (right[i] ?? 0);
  }
  return diff === 0;
}

export function isQuotaMode(value: unknown): value is QuotaMode {
  return value === "unlimited" || value === "daily" || value === "total";
}

const formatters = new Map<string, Intl.DateTimeFormat>();

function formatter(timeZone: string): Intl.DateTimeFormat {
  let existing = formatters.get(timeZone);
  if (!existing) {
    existing = new Intl.DateTimeFormat("en-US", {
      timeZone,
      hour12: false,
      year: "numeric",
      month: "2-digit",
      day: "2-digit",
      hour: "2-digit",
      minute: "2-digit",
      second: "2-digit",
    });
    formatters.set(timeZone, existing);
  }
  return existing;
}

interface WallClock {
  year: number;
  month: number;
  day: number;
  hour: number;
  minute: number;
  second: number;
}

function wallClock(ts: number, timeZone: string): WallClock {
  const parts: Record<string, number> = {};
  for (const part of formatter(timeZone).formatToParts(new Date(ts))) {
    if (part.type !== "literal") parts[part.type] = Number(part.value);
  }
  return {
    year: parts.year,
    month: parts.month,
    day: parts.day,
    hour: parts.hour === 24 ? 0 : parts.hour,
    minute: parts.minute,
    second: parts.second,
  };
}

function offsetAt(ts: number, timeZone: string): number {
  const wall = wallClock(ts, timeZone);
  const asUtc = Date.UTC(
    wall.year,
    wall.month - 1,
    wall.day,
    wall.hour,
    wall.minute,
    wall.second,
  );
  return asUtc - Math.floor(ts / 1000) * 1000;
}

/** `YYYY-MM-DD` of `ts` in `timeZone`. */
export function dayKey(ts: number, timeZone: string): string {
  const wall = wallClock(ts, timeZone);
  return `${wall.year}-${String(wall.month).padStart(2, "0")}-${String(wall.day).padStart(2, "0")}`;
}

/**
 * Epoch milliseconds of the next midnight in `timeZone` (used by the app to
 * show "resets at ..." and to roll daily limits while offline).
 */
export function nextMidnight(ts: number, timeZone: string): number {
  const wall = wallClock(ts, timeZone);
  const wallNextMidnight = Date.UTC(wall.year, wall.month - 1, wall.day + 1, 0, 0, 0);
  const firstGuess = wallNextMidnight - offsetAt(ts, timeZone);
  return wallNextMidnight - offsetAt(firstGuess, timeZone);
}

/** WebCrypto and Java use different ECDSA encodings: raw r||s vs DER. */
export function rawEcdsaToDer(raw: Uint8Array): Uint8Array {
  if (raw.length !== 64) throw new Error("unexpected ECDSA signature length");
  const r = encodeDerInteger(trimZeros(raw.subarray(0, 32)));
  const s = encodeDerInteger(trimZeros(raw.subarray(32)));
  const body = new Uint8Array(r.length + s.length);
  body.set(r);
  body.set(s, r.length);
  return wrapDer(0x30, body);
}

function trimZeros(value: Uint8Array): Uint8Array {
  let start = 0;
  while (start < value.length - 1 && value[start] === 0) start++;
  return value.subarray(start);
}

function encodeDerInteger(value: Uint8Array): Uint8Array {
  const pad = (value[0] & 0x80) !== 0;
  const body = new Uint8Array(value.length + (pad ? 1 : 0));
  if (pad) body[0] = 0;
  body.set(value, pad ? 1 : 0);
  return wrapDer(0x02, body);
}

function wrapDer(tag: number, body: Uint8Array): Uint8Array {
  if (body.length > 127) {
    const out = new Uint8Array(3 + body.length);
    out[0] = tag;
    out[1] = 0x81;
    out[2] = body.length;
    out.set(body, 3);
    return out;
  }
  const out = new Uint8Array(2 + body.length);
  out[0] = tag;
  out[1] = body.length;
  out.set(body, 2);
  return out;
}

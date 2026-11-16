export type StoredStatus = "active" | "blocked" | "cancelled";
export type DisplayStatus = "pending" | "active" | "expired" | "blocked" | "cancelled";
export type QuotaMode = "unlimited" | "daily" | "total";

export const LICENSE_ALPHABET = "23456789ABCDEFGHJKLMNPQRSTUVWXYZ";
export const DEVICE_CODE_PATTERN =
  /^TP-[2-9A-HJ-NP-Z]{4}(-[2-9A-HJ-NP-Z]{4}){3}$/;
export const LICENSE_KEY_PATTERN = /^[2-9A-HJ-NP-Z]{4}(-[2-9A-HJ-NP-Z]{4}){3}$/;

export interface LicenseRow {
  id: string;
  license_key: string;
  name: string;
  note: string | null;
  status: StoredStatus;
  device_code: string;
  duration_days: number | null;
  created_at: number;
  activated_at: number | null;
  expires_at: number | null;
  device_model: string | null;
  android_version: string | null;
  app_version: string | null;
  last_seen_at: number | null;
  quota_mode: QuotaMode;
  quota_limit: number | null;
  used_total: number;
  used_today: number;
  used_day: string | null;
}

export interface EventRow {
  id?: number;
  license_id: string;
  ts: number;
  type: string;
  detail: string | null;
}

export type EventType =
  | "created"
  | "activated"
  | "blocked"
  | "unblocked"
  | "cancelled"
  | "extended"
  | "moved_phone"
  | "wrong_device_attempt"
  | "name_changed"
  | "quota_changed"
  | "usage_reset";

export interface QuotaInfo {
  mode: QuotaMode;
  limit: number | null;
  usedToday: number;
  usedTotal: number;
  remaining: number | null;
  dayEndsAt: number;
}

export interface VerifyPayload {
  status:
    | "ACTIVE"
    | "EXPIRED"
    | "BLOCKED"
    | "CANCELLED"
    | "WRONG_DEVICE"
    | "INVALID_KEY";
  name: string | null;
  expiresAt: number | null;
  activatedAt: number | null;
  serverTime: number;
  nonce: string;
  quota: QuotaInfo | null;
  reportedId: string | null;
}

export interface VerifyRequest {
  key: string;
  deviceCode: string;
  deviceModel: string | null;
  androidVersion: string | null;
  appVersion: string | null;
  nonce: string;
  usageCount: number;
  reportId: string | null;
}

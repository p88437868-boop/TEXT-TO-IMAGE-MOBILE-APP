-- TextPic Studio license server — initial schema.
-- Run with:  npm run db:migrate:local   (local test database)
--            npm run db:migrate:remote  (real Cloudflare D1)
--
-- All timestamps are Unix epoch milliseconds (integers).

CREATE TABLE IF NOT EXISTS licenses (
  id              TEXT PRIMARY KEY,
  license_key     TEXT NOT NULL UNIQUE,
  name            TEXT NOT NULL,
  note            TEXT,
  status          TEXT NOT NULL DEFAULT 'active',   -- active | blocked | cancelled
  device_code     TEXT NOT NULL,
  duration_days   INTEGER,                          -- NULL = lifetime
  created_at      INTEGER NOT NULL,
  activated_at    INTEGER,
  expires_at      INTEGER,
  device_model    TEXT,
  android_version TEXT,
  app_version     TEXT,
  last_seen_at    INTEGER,
  quota_mode      TEXT NOT NULL DEFAULT 'unlimited', -- unlimited | daily | total
  quota_limit     INTEGER,
  used_total      INTEGER NOT NULL DEFAULT 0,
  used_today      INTEGER NOT NULL DEFAULT 0,
  used_day        TEXT                              -- YYYY-MM-DD in QUOTA_TZ
);

CREATE UNIQUE INDEX IF NOT EXISTS idx_licenses_key ON licenses(license_key);
CREATE INDEX IF NOT EXISTS idx_licenses_device ON licenses(device_code);

CREATE TABLE IF NOT EXISTS events (
  id         INTEGER PRIMARY KEY AUTOINCREMENT,
  license_id TEXT NOT NULL,
  ts         INTEGER NOT NULL,
  type       TEXT NOT NULL,
  detail     TEXT
);

CREATE INDEX IF NOT EXISTS idx_events_license ON events(license_id, ts DESC);

-- One row per reported batch of rendered images, so a retried report is
-- never counted twice.
CREATE TABLE IF NOT EXISTS usage_reports (
  report_id  TEXT PRIMARY KEY,
  license_id TEXT NOT NULL,
  ts         INTEGER NOT NULL,
  count      INTEGER NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_usage_reports_ts ON usage_reports(ts);

CREATE TABLE IF NOT EXISTS settings (
  key   TEXT PRIMARY KEY,
  value TEXT
);

-- Failed activation attempts, per hour, per IP + device code.
CREATE TABLE IF NOT EXISTS rate_limits (
  bucket   TEXT PRIMARY KEY,
  count    INTEGER NOT NULL,
  reset_at INTEGER NOT NULL
);

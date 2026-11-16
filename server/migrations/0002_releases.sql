-- In-app updates: releases and their 512 KB chunks.
-- The license tables are untouched; this only adds new tables.

CREATE TABLE IF NOT EXISTS releases (
  version_code INTEGER PRIMARY KEY,
  version_name TEXT NOT NULL,
  notes TEXT NOT NULL DEFAULT '',
  sha256 TEXT NOT NULL,
  size_bytes INTEGER NOT NULL,
  chunk_count INTEGER NOT NULL,
  chunk_size INTEGER NOT NULL,
  mandatory INTEGER NOT NULL DEFAULT 0,
  status TEXT NOT NULL DEFAULT 'draft',
  created_at INTEGER NOT NULL,
  published_at INTEGER
);

CREATE TABLE IF NOT EXISTS release_chunks (
  version_code INTEGER NOT NULL,
  idx INTEGER NOT NULL,
  sha256 TEXT NOT NULL,
  data BLOB NOT NULL,
  PRIMARY KEY (version_code, idx)
);

CREATE TABLE IF NOT EXISTS release_events (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  version_code INTEGER NOT NULL,
  ts INTEGER NOT NULL,
  type TEXT NOT NULL,
  detail TEXT
);

CREATE INDEX IF NOT EXISTS idx_releases_status ON releases(status);
CREATE INDEX IF NOT EXISTS idx_release_chunks_version ON release_chunks(version_code);

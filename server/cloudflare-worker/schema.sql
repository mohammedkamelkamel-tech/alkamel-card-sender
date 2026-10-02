CREATE TABLE IF NOT EXISTS licenses (
  token TEXT PRIMARY KEY,
  type TEXT NOT NULL CHECK(type IN ('DAY','LIFE')),
  device_id TEXT,
  activated_at INTEGER,
  expires_at INTEGER
);
CREATE INDEX IF NOT EXISTS idx_licenses_device ON licenses(device_id);

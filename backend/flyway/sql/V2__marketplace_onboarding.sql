CREATE TABLE IF NOT EXISTS target_registrations (
  id TEXT PRIMARY KEY,
  payload_json JSONB NOT NULL,
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS marketplace_events (
  id TEXT PRIMARY KEY,
  payload_json JSONB NOT NULL,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_target_registrations_updated_at
  ON target_registrations (updated_at DESC);

CREATE INDEX IF NOT EXISTS idx_marketplace_events_created_at
  ON marketplace_events (created_at DESC);

CREATE TABLE IF NOT EXISTS forwarder_logs (
  id TEXT PRIMARY KEY,
  payload_json JSONB NOT NULL,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_forwarder_logs_created_at
  ON forwarder_logs (created_at DESC);

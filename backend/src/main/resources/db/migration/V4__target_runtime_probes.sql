CREATE TYPE mappo_runtime_probe_status AS ENUM ('unknown', 'healthy', 'unhealthy', 'unreachable');

CREATE TABLE target_runtime_probes (
  target_id VARCHAR(128) PRIMARY KEY REFERENCES targets (id) ON DELETE CASCADE,
  runtime_status mappo_runtime_probe_status NOT NULL,
  checked_at TIMESTAMPTZ NOT NULL,
  endpoint_url VARCHAR(2048) NULL,
  http_status_code INTEGER NULL,
  summary TEXT NOT NULL DEFAULT '',
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_target_runtime_probes_status_checked_at
  ON target_runtime_probes (runtime_status, checked_at DESC);

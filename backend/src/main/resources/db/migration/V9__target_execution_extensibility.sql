CREATE TABLE target_external_execution_handles (
  run_id VARCHAR(128) NOT NULL,
  target_id VARCHAR(128) NOT NULL,
  provider VARCHAR(64) NOT NULL,
  execution_id VARCHAR(255) NULL,
  execution_name VARCHAR(255) NULL,
  execution_status VARCHAR(64) NULL,
  execution_url VARCHAR(2048) NULL,
  logs_url VARCHAR(2048) NULL,
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  PRIMARY KEY (run_id, target_id),
  FOREIGN KEY (run_id, target_id)
    REFERENCES target_execution_records (run_id, target_id)
    ON DELETE CASCADE
);

CREATE INDEX idx_target_external_execution_handles_updated_at
  ON target_external_execution_handles (updated_at DESC);

CREATE TABLE target_execution_config_entries (
  target_id VARCHAR(128) NOT NULL REFERENCES targets (id) ON DELETE CASCADE,
  config_key VARCHAR(128) NOT NULL,
  config_value VARCHAR(2048) NOT NULL,
  PRIMARY KEY (target_id, config_key)
);

CREATE INDEX idx_target_execution_config_entries_target_id
  ON target_execution_config_entries (target_id);

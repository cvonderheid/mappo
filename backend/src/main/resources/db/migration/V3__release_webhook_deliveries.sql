CREATE TYPE mappo_release_webhook_status AS ENUM ('applied', 'skipped', 'failed');

CREATE TABLE release_webhook_deliveries (
  id VARCHAR(128) PRIMARY KEY,
  external_delivery_id VARCHAR(128) NULL,
  event_type VARCHAR(64) NOT NULL,
  repo VARCHAR(255) NULL,
  ref VARCHAR(255) NULL,
  manifest_path VARCHAR(512) NULL,
  status mappo_release_webhook_status NOT NULL,
  message TEXT NOT NULL,
  changed_paths_text TEXT NULL,
  manifest_release_count INTEGER NOT NULL DEFAULT 0,
  created_count INTEGER NOT NULL DEFAULT 0,
  skipped_count INTEGER NOT NULL DEFAULT 0,
  ignored_count INTEGER NOT NULL DEFAULT 0,
  created_release_ids_text TEXT NULL,
  received_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_release_webhook_deliveries_received_at
  ON release_webhook_deliveries (received_at DESC);

CREATE INDEX idx_release_webhook_deliveries_external_delivery_id
  ON release_webhook_deliveries (external_delivery_id);

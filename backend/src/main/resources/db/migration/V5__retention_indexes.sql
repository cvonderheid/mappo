CREATE INDEX idx_runs_status_created_at
  ON runs (status, created_at DESC);

CREATE INDEX idx_marketplace_events_status_created_at
  ON marketplace_events (status, created_at DESC);

CREATE INDEX idx_forwarder_logs_level_created_at
  ON forwarder_logs (level, created_at DESC);

CREATE INDEX idx_release_webhook_deliveries_status_received_at
  ON release_webhook_deliveries (status, received_at DESC);

CREATE INDEX idx_target_execution_records_run_status_updated_at
  ON target_execution_records (run_id, status, updated_at DESC);

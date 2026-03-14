CREATE TABLE project_configuration_audit_events (
  id VARCHAR(128) PRIMARY KEY,
  project_id VARCHAR(128) NOT NULL REFERENCES projects (id) ON DELETE CASCADE,
  action VARCHAR(32) NOT NULL,
  actor VARCHAR(128) NULL,
  change_summary TEXT NOT NULL,
  before_snapshot JSONB NULL,
  after_snapshot JSONB NOT NULL,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_project_configuration_audit_events_project_created_at
  ON project_configuration_audit_events (project_id, created_at DESC);


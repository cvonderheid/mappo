CREATE TYPE mappo_health_status AS ENUM ('registered', 'healthy', 'degraded');
CREATE TYPE mappo_simulated_failure_mode AS ENUM (
  'none',
  'validate_once',
  'deploy_once',
  'verify_once'
);
CREATE TYPE mappo_strategy_mode AS ENUM ('all_at_once', 'waves');
CREATE TYPE mappo_run_status AS ENUM ('running', 'succeeded', 'failed', 'partial', 'halted');
CREATE TYPE mappo_target_stage AS ENUM (
  'QUEUED',
  'VALIDATING',
  'DEPLOYING',
  'VERIFYING',
  'SUCCEEDED',
  'FAILED'
);
CREATE TYPE mappo_marketplace_event_status AS ENUM ('applied', 'duplicate', 'rejected');
CREATE TYPE mappo_forwarder_log_level AS ENUM ('info', 'warning', 'error');

CREATE TABLE targets (
  id TEXT PRIMARY KEY,
  tenant_id UUID NOT NULL,
  subscription_id UUID NOT NULL,
  managed_app_id TEXT NOT NULL,
  customer_name TEXT NULL,
  last_deployed_release TEXT NOT NULL,
  health_status mappo_health_status NOT NULL,
  last_check_in_at TIMESTAMPTZ NOT NULL,
  simulated_failure_mode mappo_simulated_failure_mode NOT NULL DEFAULT 'none',
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE target_tags (
  target_id TEXT NOT NULL REFERENCES targets (id) ON DELETE CASCADE,
  tag_key TEXT NOT NULL,
  tag_value TEXT NOT NULL,
  PRIMARY KEY (target_id, tag_key)
);

CREATE INDEX idx_target_tags_key_value
  ON target_tags (tag_key, tag_value);

CREATE TABLE target_registrations (
  target_id TEXT PRIMARY KEY,
  display_name TEXT NOT NULL,
  managed_application_id TEXT NULL,
  managed_resource_group_id TEXT NOT NULL,
  metadata JSONB NOT NULL DEFAULT '{}'::jsonb,
  last_event_id TEXT NULL,
  created_at TIMESTAMPTZ NOT NULL,
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_target_registrations_updated_at
  ON target_registrations (updated_at DESC);

CREATE TABLE releases (
  id TEXT PRIMARY KEY,
  template_spec_id TEXT NOT NULL,
  template_spec_version TEXT NOT NULL,
  release_notes TEXT NOT NULL DEFAULT '',
  created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_releases_created_at
  ON releases (created_at DESC);

CREATE TABLE release_parameter_defaults (
  release_id TEXT NOT NULL REFERENCES releases (id) ON DELETE CASCADE,
  param_key TEXT NOT NULL,
  param_value TEXT NOT NULL,
  PRIMARY KEY (release_id, param_key)
);

CREATE TABLE release_verification_hints (
  release_id TEXT NOT NULL REFERENCES releases (id) ON DELETE CASCADE,
  position INTEGER NOT NULL,
  hint TEXT NOT NULL,
  PRIMARY KEY (release_id, position)
);

CREATE TABLE runs (
  id TEXT PRIMARY KEY,
  release_id TEXT NOT NULL,
  strategy_mode mappo_strategy_mode NOT NULL,
  wave_tag TEXT NOT NULL,
  concurrency INTEGER NOT NULL,
  subscription_concurrency INTEGER NOT NULL DEFAULT 1,
  stop_policy_max_failure_count INTEGER NULL,
  stop_policy_max_failure_rate DOUBLE PRECISION NULL,
  status mappo_run_status NOT NULL,
  halt_reason TEXT NULL,
  created_at TIMESTAMPTZ NOT NULL,
  started_at TIMESTAMPTZ NULL,
  ended_at TIMESTAMPTZ NULL,
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_runs_created_at
  ON runs (created_at DESC);

CREATE INDEX idx_runs_updated_at
  ON runs (updated_at DESC);

CREATE TABLE run_wave_order (
  run_id TEXT NOT NULL REFERENCES runs (id) ON DELETE CASCADE,
  position INTEGER NOT NULL,
  wave_value TEXT NOT NULL,
  PRIMARY KEY (run_id, position)
);

CREATE TABLE run_guardrail_warnings (
  run_id TEXT NOT NULL REFERENCES runs (id) ON DELETE CASCADE,
  position INTEGER NOT NULL,
  warning TEXT NOT NULL,
  PRIMARY KEY (run_id, position)
);

CREATE TABLE run_targets (
  run_id TEXT NOT NULL REFERENCES runs (id) ON DELETE CASCADE,
  position INTEGER NOT NULL,
  target_id TEXT NOT NULL,
  PRIMARY KEY (run_id, position)
);

CREATE INDEX idx_run_targets_target_id
  ON run_targets (target_id);

CREATE TABLE target_execution_records (
  run_id TEXT NOT NULL REFERENCES runs (id) ON DELETE CASCADE,
  target_id TEXT NOT NULL,
  subscription_id UUID NOT NULL,
  tenant_id UUID NOT NULL,
  status mappo_target_stage NOT NULL,
  attempt INTEGER NOT NULL DEFAULT 0,
  updated_at TIMESTAMPTZ NOT NULL,
  PRIMARY KEY (run_id, target_id)
);

CREATE TABLE target_stage_records (
  run_id TEXT NOT NULL,
  target_id TEXT NOT NULL,
  position INTEGER NOT NULL,
  stage mappo_target_stage NOT NULL,
  started_at TIMESTAMPTZ NOT NULL,
  ended_at TIMESTAMPTZ NULL,
  message TEXT NOT NULL DEFAULT '',
  error_code TEXT NULL,
  error_message TEXT NULL,
  error_details JSONB NULL,
  correlation_id TEXT NOT NULL,
  portal_link TEXT NOT NULL,
  PRIMARY KEY (run_id, target_id, position),
  FOREIGN KEY (run_id, target_id)
    REFERENCES target_execution_records (run_id, target_id)
    ON DELETE CASCADE
);

CREATE TABLE target_log_events (
  run_id TEXT NOT NULL,
  target_id TEXT NOT NULL,
  position INTEGER NOT NULL,
  event_timestamp TIMESTAMPTZ NOT NULL,
  level mappo_forwarder_log_level NOT NULL,
  stage mappo_target_stage NOT NULL,
  message TEXT NOT NULL,
  correlation_id TEXT NOT NULL,
  PRIMARY KEY (run_id, target_id, position),
  FOREIGN KEY (run_id, target_id)
    REFERENCES target_execution_records (run_id, target_id)
    ON DELETE CASCADE
);

CREATE TABLE marketplace_events (
  id TEXT PRIMARY KEY,
  event_type TEXT NOT NULL,
  status mappo_marketplace_event_status NOT NULL,
  message TEXT NOT NULL,
  target_id TEXT NULL,
  tenant_id UUID NOT NULL,
  subscription_id UUID NOT NULL,
  payload JSONB NOT NULL DEFAULT '{}'::jsonb,
  created_at TIMESTAMPTZ NOT NULL,
  processed_at TIMESTAMPTZ NULL
);

CREATE INDEX idx_marketplace_events_created_at
  ON marketplace_events (created_at DESC);

CREATE TABLE forwarder_logs (
  id TEXT PRIMARY KEY,
  level mappo_forwarder_log_level NOT NULL,
  message TEXT NOT NULL,
  event_id TEXT NULL,
  event_type TEXT NULL,
  target_id TEXT NULL,
  tenant_id UUID NULL,
  subscription_id UUID NULL,
  function_app_name TEXT NULL,
  forwarder_request_id TEXT NULL,
  backend_status_code INTEGER NULL,
  details JSONB NOT NULL DEFAULT '{}'::jsonb,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_forwarder_logs_created_at
  ON forwarder_logs (created_at DESC);

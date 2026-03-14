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
CREATE TYPE mappo_marketplace_event_type AS ENUM (
  'subscription_purchased',
  'subscription_suspended',
  'subscription_deleted',
  'unknown'
);
CREATE TYPE mappo_forwarder_log_level AS ENUM ('info', 'warning', 'error');
CREATE TYPE mappo_release_source_type AS ENUM (
  'template_spec',
  'bicep',
  'deployment_stack',
  'external_deployment_inputs'
);
CREATE TYPE mappo_deployment_scope AS ENUM ('resource_group', 'subscription');
CREATE TYPE mappo_arm_deployment_mode AS ENUM ('incremental', 'complete');
CREATE TYPE mappo_registry_auth_mode AS ENUM (
  'none',
  'shared_service_principal_secret',
  'customer_managed_secret'
);
CREATE TYPE mappo_release_webhook_status AS ENUM ('applied', 'skipped', 'failed');
CREATE TYPE mappo_runtime_probe_status AS ENUM ('unknown', 'healthy', 'unhealthy', 'unreachable');
CREATE TYPE mappo_project_access_strategy AS ENUM (
  'simulator',
  'azure_workload_rbac',
  'lighthouse_delegated_access'
);
CREATE TYPE mappo_project_deployment_driver AS ENUM (
  'azure_deployment_stack',
  'azure_template_spec',
  'pipeline_trigger'
);
CREATE TYPE mappo_project_release_artifact_source AS ENUM (
  'blob_arm_template',
  'template_spec_resource',
  'external_deployment_inputs'
);
CREATE TYPE mappo_project_runtime_health_provider AS ENUM (
  'azure_container_app_http',
  'http_endpoint'
);

CREATE TABLE projects (
  id VARCHAR(128) PRIMARY KEY,
  name VARCHAR(255) NOT NULL UNIQUE,
  access_strategy mappo_project_access_strategy NOT NULL,
  access_strategy_config JSONB NOT NULL DEFAULT '{}'::jsonb,
  deployment_driver mappo_project_deployment_driver NOT NULL,
  deployment_driver_config JSONB NOT NULL DEFAULT '{}'::jsonb,
  release_artifact_source mappo_project_release_artifact_source NOT NULL,
  release_artifact_source_config JSONB NOT NULL DEFAULT '{}'::jsonb,
  runtime_health_provider mappo_project_runtime_health_provider NOT NULL,
  runtime_health_provider_config JSONB NOT NULL DEFAULT '{}'::jsonb,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

INSERT INTO projects (
  id,
  name,
  access_strategy,
  access_strategy_config,
  deployment_driver,
  deployment_driver_config,
  release_artifact_source,
  release_artifact_source_config,
  runtime_health_provider,
  runtime_health_provider_config
) VALUES
  (
    'azure-managed-app-deployment-stack',
    'Azure Managed App Deployment Stack',
    'azure_workload_rbac',
    '{"authModel":"provider_service_principal","requiresAzureCredential":true,"requiresTargetExecutionMetadata":true}'::jsonb,
    'azure_deployment_stack',
    '{"supportsPreview":true,"previewMode":"ARM_WHAT_IF","supportsExternalExecutionHandle":false}'::jsonb,
    'blob_arm_template',
    '{"descriptor":"blob_uri_manifest","templateUriField":"templateUri"}'::jsonb,
    'azure_container_app_http',
    '{"path":"/health","expectedStatus":200,"timeoutMs":5000}'::jsonb
  ),
  (
    'azure-managed-app-template-spec',
    'Azure Managed App Template Spec',
    'azure_workload_rbac',
    '{"authModel":"provider_service_principal","requiresAzureCredential":true,"requiresTargetExecutionMetadata":true}'::jsonb,
    'azure_template_spec',
    '{"supportsPreview":false,"supportsExternalExecutionHandle":false}'::jsonb,
    'template_spec_resource',
    '{"descriptor":"template_spec_release","versionRefField":"sourceVersionRef"}'::jsonb,
    'azure_container_app_http',
    '{"path":"/health","expectedStatus":200,"timeoutMs":5000}'::jsonb
  ),
  (
    'azure-appservice-ado-pipeline',
    'Azure App Service ADO Pipeline',
    'azure_workload_rbac',
    '{"authModel":"ado_service_connection","requiresAzureCredential":false,"requiresTargetExecutionMetadata":true}'::jsonb,
    'pipeline_trigger',
    '{"pipelineSystem":"azure_devops","organization":"","project":"","pipelineId":"","branch":"main","azureServiceConnectionName":"","supportsExternalExecutionHandle":true,"supportsExternalLogs":true}'::jsonb,
    'external_deployment_inputs',
    '{"sourceSystem":"azure_devops","descriptorPath":"pipelineInputs","versionField":"artifactVersion"}'::jsonb,
    'http_endpoint',
    '{"path":"/health","expectedStatus":200,"timeoutMs":5000}'::jsonb
  );

CREATE TABLE targets (
  id VARCHAR(128) PRIMARY KEY,
  project_id VARCHAR(128) NOT NULL REFERENCES projects (id),
  tenant_id UUID NOT NULL,
  subscription_id UUID NOT NULL,
  last_deployed_release VARCHAR(128) NOT NULL,
  health_status mappo_health_status NOT NULL,
  last_check_in_at TIMESTAMPTZ NOT NULL,
  simulated_failure_mode mappo_simulated_failure_mode NOT NULL DEFAULT 'none',
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  UNIQUE (tenant_id, subscription_id)
);

CREATE INDEX idx_targets_project_id
  ON targets (project_id);

CREATE TABLE target_registrations (
  target_id VARCHAR(128) PRIMARY KEY REFERENCES targets (id) ON DELETE CASCADE,
  display_name VARCHAR(255) NOT NULL,
  customer_name VARCHAR(255) NULL,
  managed_application_id VARCHAR(2048) NULL,
  managed_resource_group_id VARCHAR(2048) NULL,
  container_app_resource_id VARCHAR(2048) NULL,
  container_app_name VARCHAR(255) NULL,
  registration_source VARCHAR(64) NULL,
  last_event_id VARCHAR(128) NULL,
  deployment_stack_name VARCHAR(128) NULL,
  registry_auth_mode mappo_registry_auth_mode NULL,
  registry_server VARCHAR(255) NULL,
  registry_username VARCHAR(255) NULL,
  registry_password_secret_name VARCHAR(255) NULL,
  created_at TIMESTAMPTZ NOT NULL,
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_target_registrations_updated_at
  ON target_registrations (updated_at DESC);

CREATE TABLE target_tags (
  target_id VARCHAR(128) NOT NULL REFERENCES targets (id) ON DELETE CASCADE,
  tag_key VARCHAR(64) NOT NULL,
  tag_value VARCHAR(256) NOT NULL,
  PRIMARY KEY (target_id, tag_key)
);

CREATE INDEX idx_target_tags_key_value
  ON target_tags (tag_key, tag_value);

CREATE TABLE releases (
  id VARCHAR(128) PRIMARY KEY,
  project_id VARCHAR(128) NOT NULL REFERENCES projects (id),
  source_ref VARCHAR(2048) NOT NULL,
  source_version VARCHAR(128) NOT NULL,
  source_type mappo_release_source_type NOT NULL DEFAULT 'template_spec',
  source_version_ref VARCHAR(2048) NULL,
  deployment_scope mappo_deployment_scope NOT NULL DEFAULT 'resource_group',
  arm_deployment_mode mappo_arm_deployment_mode NOT NULL DEFAULT 'incremental',
  what_if_on_canary BOOLEAN NOT NULL DEFAULT FALSE,
  verify_after_deploy BOOLEAN NOT NULL DEFAULT TRUE,
  release_notes TEXT NOT NULL DEFAULT '',
  created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_releases_created_at
  ON releases (created_at DESC);

CREATE INDEX idx_releases_project_id
  ON releases (project_id);

CREATE TABLE release_parameter_defaults (
  release_id VARCHAR(128) NOT NULL REFERENCES releases (id) ON DELETE CASCADE,
  param_key VARCHAR(128) NOT NULL,
  param_value VARCHAR(2048) NOT NULL,
  PRIMARY KEY (release_id, param_key)
);

CREATE TABLE release_external_input_entries (
  release_id VARCHAR(128) NOT NULL REFERENCES releases (id) ON DELETE CASCADE,
  input_key VARCHAR(128) NOT NULL,
  input_value VARCHAR(4096) NOT NULL,
  PRIMARY KEY (release_id, input_key)
);

CREATE TABLE release_verification_hints (
  release_id VARCHAR(128) NOT NULL REFERENCES releases (id) ON DELETE CASCADE,
  position INTEGER NOT NULL,
  hint TEXT NOT NULL,
  PRIMARY KEY (release_id, position)
);

CREATE TABLE runs (
  id VARCHAR(128) PRIMARY KEY,
  project_id VARCHAR(128) NOT NULL REFERENCES projects (id),
  release_id VARCHAR(128) NOT NULL,
  execution_source_type mappo_release_source_type NOT NULL DEFAULT 'template_spec',
  strategy_mode mappo_strategy_mode NOT NULL,
  wave_tag VARCHAR(64) NOT NULL,
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

CREATE INDEX idx_runs_status_created_at
  ON runs (status, created_at DESC);

CREATE INDEX idx_runs_project_id
  ON runs (project_id);

CREATE TABLE run_wave_order (
  run_id VARCHAR(128) NOT NULL REFERENCES runs (id) ON DELETE CASCADE,
  position INTEGER NOT NULL,
  wave_value VARCHAR(128) NOT NULL,
  PRIMARY KEY (run_id, position)
);

CREATE TABLE run_guardrail_warnings (
  run_id VARCHAR(128) NOT NULL REFERENCES runs (id) ON DELETE CASCADE,
  position INTEGER NOT NULL,
  warning TEXT NOT NULL,
  PRIMARY KEY (run_id, position)
);

CREATE TABLE run_targets (
  run_id VARCHAR(128) NOT NULL REFERENCES runs (id) ON DELETE CASCADE,
  position INTEGER NOT NULL,
  target_id VARCHAR(128) NOT NULL,
  PRIMARY KEY (run_id, position)
);

CREATE INDEX idx_run_targets_target_id
  ON run_targets (target_id);

CREATE TABLE target_execution_records (
  run_id VARCHAR(128) NOT NULL REFERENCES runs (id) ON DELETE CASCADE,
  target_id VARCHAR(128) NOT NULL REFERENCES targets (id) ON DELETE CASCADE,
  subscription_id UUID NOT NULL,
  tenant_id UUID NOT NULL,
  status mappo_target_stage NOT NULL,
  attempt INTEGER NOT NULL DEFAULT 0,
  updated_at TIMESTAMPTZ NOT NULL,
  PRIMARY KEY (run_id, target_id)
);

CREATE INDEX idx_target_execution_records_run_status_updated_at
  ON target_execution_records (run_id, status, updated_at DESC);

CREATE TABLE target_stage_records (
  run_id VARCHAR(128) NOT NULL,
  target_id VARCHAR(128) NOT NULL,
  position INTEGER NOT NULL,
  stage mappo_target_stage NOT NULL,
  started_at TIMESTAMPTZ NOT NULL,
  ended_at TIMESTAMPTZ NULL,
  message TEXT NOT NULL DEFAULT '',
  error_code VARCHAR(128) NULL,
  error_message TEXT NULL,
  error_status_code INTEGER NULL,
  error_detail_text TEXT NULL,
  error_desired_image VARCHAR(2048) NULL,
  azure_error_code VARCHAR(128) NULL,
  azure_error_message TEXT NULL,
  azure_request_id VARCHAR(128) NULL,
  azure_arm_service_request_id VARCHAR(128) NULL,
  azure_correlation_id VARCHAR(128) NULL,
  azure_deployment_name VARCHAR(128) NULL,
  azure_operation_id VARCHAR(128) NULL,
  azure_resource_id VARCHAR(2048) NULL,
  correlation_id VARCHAR(128) NOT NULL,
  portal_link VARCHAR(2048) NOT NULL,
  PRIMARY KEY (run_id, target_id, position),
  FOREIGN KEY (run_id, target_id)
    REFERENCES target_execution_records (run_id, target_id)
    ON DELETE CASCADE
);

CREATE TABLE target_log_events (
  run_id VARCHAR(128) NOT NULL,
  target_id VARCHAR(128) NOT NULL,
  position INTEGER NOT NULL,
  event_timestamp TIMESTAMPTZ NOT NULL,
  level mappo_forwarder_log_level NOT NULL,
  stage mappo_target_stage NOT NULL,
  message TEXT NOT NULL,
  correlation_id VARCHAR(128) NOT NULL,
  PRIMARY KEY (run_id, target_id, position),
  FOREIGN KEY (run_id, target_id)
    REFERENCES target_execution_records (run_id, target_id)
    ON DELETE CASCADE
);

CREATE TABLE marketplace_events (
  id VARCHAR(128) PRIMARY KEY,
  event_type mappo_marketplace_event_type NOT NULL,
  status mappo_marketplace_event_status NOT NULL,
  message TEXT NOT NULL,
  target_id VARCHAR(128) NULL,
  tenant_id UUID NOT NULL,
  subscription_id UUID NOT NULL,
  display_name VARCHAR(255) NULL,
  customer_name VARCHAR(255) NULL,
  managed_application_id VARCHAR(2048) NULL,
  managed_resource_group_id VARCHAR(2048) NULL,
  container_app_resource_id VARCHAR(2048) NULL,
  container_app_name VARCHAR(255) NULL,
  target_group VARCHAR(64) NULL,
  region VARCHAR(64) NULL,
  environment VARCHAR(64) NULL,
  tier VARCHAR(64) NULL,
  last_deployed_release VARCHAR(128) NULL,
  health_status mappo_health_status NULL,
  registration_source VARCHAR(64) NULL,
  marketplace_payload_id VARCHAR(128) NULL,
  created_at TIMESTAMPTZ NOT NULL,
  processed_at TIMESTAMPTZ NULL
);

CREATE INDEX idx_marketplace_events_created_at
  ON marketplace_events (created_at DESC);

CREATE INDEX idx_marketplace_events_status_created_at
  ON marketplace_events (status, created_at DESC);

CREATE TABLE forwarder_logs (
  id VARCHAR(128) PRIMARY KEY,
  level mappo_forwarder_log_level NOT NULL,
  message TEXT NOT NULL,
  event_id VARCHAR(128) NULL,
  event_type mappo_marketplace_event_type NULL,
  target_id VARCHAR(128) NULL,
  tenant_id UUID NULL,
  subscription_id UUID NULL,
  function_app_name VARCHAR(255) NULL,
  forwarder_request_id VARCHAR(128) NULL,
  backend_status_code INTEGER NULL,
  detail_text TEXT NULL,
  backend_response_body TEXT NULL,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_forwarder_logs_created_at
  ON forwarder_logs (created_at DESC);

CREATE INDEX idx_forwarder_logs_level_created_at
  ON forwarder_logs (level, created_at DESC);

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

CREATE INDEX idx_release_webhook_deliveries_status_received_at
  ON release_webhook_deliveries (status, received_at DESC);

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

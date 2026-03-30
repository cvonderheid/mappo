CREATE TABLE provider_connections (
  id VARCHAR(128) PRIMARY KEY,
  name VARCHAR(255) NOT NULL UNIQUE,
  provider mappo_release_ingest_provider NOT NULL,
  enabled BOOLEAN NOT NULL DEFAULT true,
  organization_filter VARCHAR(255),
  personal_access_token_ref VARCHAR(255),
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_provider_connections_provider
  ON provider_connections (provider);

ALTER TABLE projects
  ADD COLUMN provider_connection_id VARCHAR(128)
    REFERENCES provider_connections (id) ON DELETE SET NULL;

CREATE INDEX idx_projects_provider_connection_id
  ON projects (provider_connection_id);

INSERT INTO provider_connections (
  id,
  name,
  provider,
  enabled,
  organization_filter,
  personal_access_token_ref
) VALUES (
  'ado-default',
  'Azure DevOps Default Connection',
  'azure_devops',
  true,
  NULL,
  'mappo.azure-devops.personal-access-token'
);

UPDATE projects
SET provider_connection_id = 'ado-default'
WHERE deployment_driver::text = 'pipeline_trigger'
  AND (provider_connection_id IS NULL OR btrim(provider_connection_id) = '');

ALTER TABLE release_ingest_endpoints
  DROP CONSTRAINT IF EXISTS release_ingest_endpoints_provider_api_secret_ref_check;

ALTER TABLE release_ingest_endpoints
  DROP COLUMN IF EXISTS provider_api_secret_ref;

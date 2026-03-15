CREATE TYPE mappo_release_ingest_provider AS ENUM (
  'github',
  'azure_devops'
);

CREATE TABLE release_ingest_endpoints (
  id VARCHAR(128) PRIMARY KEY,
  name VARCHAR(255) NOT NULL UNIQUE,
  provider mappo_release_ingest_provider NOT NULL,
  enabled BOOLEAN NOT NULL DEFAULT true,
  secret_ref VARCHAR(255),
  repo_filter VARCHAR(255),
  branch_filter VARCHAR(255),
  pipeline_id_filter VARCHAR(128),
  manifest_path VARCHAR(512),
  source_config JSONB NOT NULL DEFAULT '{}'::jsonb,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_release_ingest_endpoints_provider
  ON release_ingest_endpoints (provider);

ALTER TABLE projects
  ADD COLUMN release_ingest_endpoint_id VARCHAR(128)
    REFERENCES release_ingest_endpoints (id) ON DELETE SET NULL;

CREATE INDEX idx_projects_release_ingest_endpoint_id
  ON projects (release_ingest_endpoint_id);

INSERT INTO release_ingest_endpoints (
  id,
  name,
  provider,
  enabled,
  secret_ref,
  repo_filter,
  branch_filter,
  manifest_path,
  source_config
) VALUES
  (
    'github-managed-app-default',
    'GitHub Managed App Default',
    'github',
    true,
    'mappo.managed-app-release.webhook-secret',
    'cvonderheid/mappo-managed-app',
    'main',
    'releases/releases.manifest.json',
    '{}'::jsonb
  ),
  (
    'ado-pipeline-default',
    'Azure DevOps Pipeline Default',
    'azure_devops',
    true,
    'mappo.azure-devops.webhook-secret',
    NULL,
    NULL,
    NULL,
    '{}'::jsonb
  );

UPDATE projects
SET release_ingest_endpoint_id = 'github-managed-app-default'
WHERE id IN ('azure-managed-app-deployment-stack', 'azure-managed-app-template-spec');

UPDATE projects
SET release_ingest_endpoint_id = 'ado-pipeline-default'
WHERE id = 'azure-appservice-ado-pipeline';

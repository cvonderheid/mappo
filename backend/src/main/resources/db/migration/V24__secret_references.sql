CREATE TABLE secret_references (
  id VARCHAR(128) PRIMARY KEY,
  name VARCHAR(255) NOT NULL UNIQUE,
  provider VARCHAR(64) NOT NULL,
  usage VARCHAR(64) NOT NULL,
  mode VARCHAR(64) NOT NULL,
  backend_ref VARCHAR(255) NOT NULL,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_secret_references_provider_usage
  ON secret_references (provider, usage);

INSERT INTO secret_references (id, name, provider, usage, mode, backend_ref)
VALUES
  ('ado-runtime-pat', 'Azure DevOps Runtime PAT', 'azure_devops', 'deployment_api_credential', 'mappo_default', 'mappo.azure-devops.personal-access-token'),
  ('ado-webhook-secret', 'Azure DevOps Webhook Secret', 'azure_devops', 'webhook_verification', 'mappo_default', 'mappo.azure-devops.webhook-secret'),
  ('github-release-webhook', 'GitHub Release Webhook Secret', 'github', 'webhook_verification', 'mappo_default', 'mappo.managed-app-release.webhook-secret');

UPDATE provider_connections
SET personal_access_token_ref = 'secret:ado-runtime-pat'
WHERE provider::text = 'azure_devops'
  AND (
    personal_access_token_ref IS NULL
    OR btrim(personal_access_token_ref) = ''
    OR btrim(personal_access_token_ref) = 'mappo.azure-devops.personal-access-token'
  );

UPDATE release_ingest_endpoints
SET secret_ref = 'secret:ado-webhook-secret'
WHERE provider::text = 'azure_devops'
  AND (
    secret_ref IS NULL
    OR btrim(secret_ref) = ''
    OR btrim(secret_ref) = 'mappo.azure-devops.webhook-secret'
  );

UPDATE release_ingest_endpoints
SET secret_ref = 'secret:github-release-webhook'
WHERE provider::text = 'github'
  AND (
    secret_ref IS NULL
    OR btrim(secret_ref) = ''
    OR btrim(secret_ref) = 'mappo.managed-app-release.webhook-secret'
  );

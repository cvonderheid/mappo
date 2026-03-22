-- Normalize legacy secret reference formats to the simplified model:
-- - server-managed key (default) or inline literal for PAT/webhook secrets.

UPDATE projects
SET deployment_driver_config = jsonb_set(
  deployment_driver_config,
  '{personalAccessTokenRef}',
  to_jsonb('mappo.azure-devops.personal-access-token'::text),
  true
)
WHERE deployment_driver = 'pipeline_trigger'
  AND COALESCE(deployment_driver_config ->> 'personalAccessTokenRef', '') <> ''
  AND COALESCE(deployment_driver_config ->> 'personalAccessTokenRef', '') NOT LIKE 'literal:%'
  AND COALESCE(deployment_driver_config ->> 'personalAccessTokenRef', '') <> 'mappo.azure-devops.personal-access-token';

UPDATE release_ingest_endpoints
SET secret_ref = CASE
  WHEN provider = 'azure_devops' THEN 'mappo.azure-devops.webhook-secret'
  ELSE 'mappo.managed-app-release.webhook-secret'
END
WHERE COALESCE(secret_ref, '') <> ''
  AND secret_ref NOT LIKE 'literal:%'
  AND secret_ref NOT IN (
    'mappo.azure-devops.webhook-secret',
    'mappo.managed-app-release.webhook-secret'
  );

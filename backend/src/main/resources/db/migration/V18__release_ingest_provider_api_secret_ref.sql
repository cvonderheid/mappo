ALTER TABLE release_ingest_endpoints
  ADD COLUMN provider_api_secret_ref VARCHAR(255);

UPDATE release_ingest_endpoints
SET provider_api_secret_ref = CASE
  WHEN provider = 'azure_devops' THEN 'mappo.azure-devops.personal-access-token'
  ELSE NULL
END;

ALTER TABLE release_ingest_endpoints
  DROP COLUMN source_config;

UPDATE projects
SET deployment_driver_config = deployment_driver_config - 'personalAccessTokenRef'
WHERE deployment_driver_config ? 'personalAccessTokenRef';

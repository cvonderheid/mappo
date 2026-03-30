UPDATE release_ingest_endpoints
SET provider_api_secret_ref = 'mappo.azure-devops.personal-access-token'
WHERE provider::text = 'azure_devops'
  AND (provider_api_secret_ref IS NULL OR btrim(provider_api_secret_ref) = '');

UPDATE release_ingest_endpoints
SET provider_api_secret_ref = NULL
WHERE provider::text <> 'azure_devops';

ALTER TABLE release_ingest_endpoints
ADD CONSTRAINT release_ingest_endpoints_provider_api_secret_ref_check
CHECK (
  (provider::text = 'azure_devops' AND provider_api_secret_ref = 'mappo.azure-devops.personal-access-token')
  OR
  (provider::text <> 'azure_devops' AND provider_api_secret_ref IS NULL)
);

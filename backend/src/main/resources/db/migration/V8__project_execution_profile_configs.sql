ALTER TABLE projects
  ADD COLUMN access_strategy_config JSONB NOT NULL DEFAULT '{}'::jsonb,
  ADD COLUMN deployment_driver_config JSONB NOT NULL DEFAULT '{}'::jsonb,
  ADD COLUMN release_artifact_source_config JSONB NOT NULL DEFAULT '{}'::jsonb,
  ADD COLUMN runtime_health_provider_config JSONB NOT NULL DEFAULT '{}'::jsonb;

UPDATE projects
SET access_strategy_config = '{"authModel":"provider_service_principal","requiresAzureCredential":true,"requiresTargetExecutionMetadata":true}'::jsonb,
    deployment_driver_config = '{"supportsPreview":true,"previewMode":"ARM_WHAT_IF","supportsExternalExecutionHandle":false}'::jsonb,
    release_artifact_source_config = '{"descriptor":"blob_uri_manifest","templateUriField":"templateUri"}'::jsonb,
    runtime_health_provider_config = '{"path":"/health","expectedStatus":200,"timeoutMs":5000}'::jsonb
WHERE id = 'azure-managed-app-deployment-stack';

UPDATE projects
SET access_strategy_config = '{"authModel":"provider_service_principal","requiresAzureCredential":true,"requiresTargetExecutionMetadata":true}'::jsonb,
    deployment_driver_config = '{"supportsPreview":false,"supportsExternalExecutionHandle":false}'::jsonb,
    release_artifact_source_config = '{"descriptor":"template_spec_release","versionRefField":"sourceVersionRef"}'::jsonb,
    runtime_health_provider_config = '{"path":"/health","expectedStatus":200,"timeoutMs":5000}'::jsonb
WHERE id = 'azure-managed-app-template-spec';

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
) VALUES (
  'azure-appservice-ado-pipeline',
  'Azure App Service ADO Pipeline',
  'lighthouse_delegated_access',
  '{"azureServiceConnectionName":"","managingTenantId":"","managingPrincipalClientId":"","requiresDelegation":true}'::jsonb,
  'pipeline_trigger',
  '{"pipelineSystem":"azure_devops","organization":"","project":"","pipelineId":"","branch":"main","supportsExternalExecutionHandle":true,"supportsExternalLogs":true}'::jsonb,
  'external_deployment_inputs',
  '{"sourceSystem":"azure_devops","descriptorPath":"pipelineInputs","versionField":"artifactVersion"}'::jsonb,
  'http_endpoint',
  '{"path":"/health","expectedStatus":200,"timeoutMs":5000}'::jsonb
);

UPDATE projects
SET deployment_driver_config = COALESCE(deployment_driver_config, '{}'::jsonb) - 'azureServiceConnectionName'
WHERE deployment_driver = 'pipeline_trigger'
  AND COALESCE(deployment_driver_config, '{}'::jsonb) ? 'azureServiceConnectionName';

UPDATE projects
SET access_strategy_config = jsonb_set(
    jsonb_set(
      jsonb_set(
        COALESCE(access_strategy_config, '{}'::jsonb) - 'azureServiceConnectionName',
        '{authModel}',
        '"pipeline_owned"'::jsonb,
        true
      ),
      '{requiresAzureCredential}',
      'false'::jsonb,
      true
    ),
    '{requiresTargetExecutionMetadata}',
    'true'::jsonb,
    true
  )
WHERE deployment_driver = 'pipeline_trigger'
  AND COALESCE(deployment_driver_config, '{}'::jsonb) ->> 'pipelineSystem' = 'azure_devops';

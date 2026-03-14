UPDATE projects
SET access_strategy = 'azure_workload_rbac',
    access_strategy_config = '{"authModel":"ado_service_connection","requiresAzureCredential":false,"requiresTargetExecutionMetadata":true}'::jsonb,
    deployment_driver_config = jsonb_set(
      jsonb_set(
        jsonb_set(
          jsonb_set(
            COALESCE(deployment_driver_config, '{}'::jsonb),
            '{pipelineSystem}',
            '"azure_devops"'::jsonb,
            true
          ),
          '{organization}',
          '"https://dev.azure.com/pg123"'::jsonb,
          true
        ),
        '{project}',
        '"demo-app-service"'::jsonb,
        true
      ),
      '{pipelineId}',
      '"1"'::jsonb,
      true
    )
WHERE id = 'azure-appservice-ado-pipeline';

UPDATE projects
SET deployment_driver_config = jsonb_set(
      jsonb_set(
        jsonb_set(
          jsonb_set(
            jsonb_set(
              COALESCE(deployment_driver_config, '{}'::jsonb),
              '{pipelineSystem}',
              '"azure_devops"'::jsonb,
              true
            ),
            '{organization}',
            '"https://dev.azure.com/pg123"'::jsonb,
            true
          ),
          '{project}',
          '"demo-app-service"'::jsonb,
          true
        ),
        '{pipelineId}',
        '"1"'::jsonb,
        true
      ),
      '{branch}',
      COALESCE(deployment_driver_config -> 'branch', '"main"'::jsonb),
      true
    )
WHERE id = 'azure-appservice-ado-pipeline';

UPDATE projects
SET deployment_driver_config = jsonb_set(
      jsonb_set(
        jsonb_set(
          COALESCE(deployment_driver_config, '{}'::jsonb),
          '{branch}',
          COALESCE(deployment_driver_config -> 'branch', '"main"'::jsonb),
          true
        ),
        '{azureServiceConnectionName}',
        '"mappo-ado-demo-rg-contributor"'::jsonb,
        true
      ),
      '{supportsExternalExecutionHandle}',
      COALESCE(deployment_driver_config -> 'supportsExternalExecutionHandle', 'true'::jsonb),
      true
    )
WHERE id = 'azure-appservice-ado-pipeline';

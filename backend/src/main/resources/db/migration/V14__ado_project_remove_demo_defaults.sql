UPDATE projects
SET access_strategy = 'azure_workload_rbac',
    access_strategy_config = jsonb_set(
      jsonb_set(
        jsonb_set(
          COALESCE(access_strategy_config, '{}'::jsonb),
          '{authModel}',
          '"ado_service_connection"'::jsonb,
          true
        ),
        '{requiresAzureCredential}',
        'false'::jsonb,
        true
      ),
      '{requiresTargetExecutionMetadata}',
      'true'::jsonb,
      true
    ),
    deployment_driver_config = jsonb_set(
      jsonb_set(
        jsonb_set(
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
                '""'::jsonb,
                true
              ),
              '{project}',
              '""'::jsonb,
              true
            ),
            '{pipelineId}',
            '""'::jsonb,
            true
          ),
          '{branch}',
          COALESCE(deployment_driver_config -> 'branch', '"main"'::jsonb),
          true
        ),
        '{azureServiceConnectionName}',
        '""'::jsonb,
        true
      ),
      '{supportsExternalExecutionHandle}',
      COALESCE(deployment_driver_config -> 'supportsExternalExecutionHandle', 'true'::jsonb),
      true
    )
WHERE id = 'azure-appservice-ado-pipeline';

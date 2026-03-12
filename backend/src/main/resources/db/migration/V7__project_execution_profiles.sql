-- flyway:executeInTransaction=false

ALTER TYPE mappo_project_access_strategy
  ADD VALUE 'lighthouse_delegated_access';

ALTER TYPE mappo_project_deployment_driver
  ADD VALUE 'pipeline_trigger';

ALTER TYPE mappo_project_release_artifact_source
  ADD VALUE 'external_deployment_inputs';

ALTER TYPE mappo_project_runtime_health_provider
  ADD VALUE 'http_endpoint';

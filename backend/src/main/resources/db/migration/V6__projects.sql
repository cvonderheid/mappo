CREATE TYPE mappo_project_access_strategy AS ENUM (
  'simulator',
  'azure_workload_rbac'
);

CREATE TYPE mappo_project_deployment_driver AS ENUM (
  'azure_deployment_stack',
  'azure_template_spec'
);

CREATE TYPE mappo_project_release_artifact_source AS ENUM (
  'blob_arm_template',
  'template_spec_resource'
);

CREATE TYPE mappo_project_runtime_health_provider AS ENUM (
  'azure_container_app_http'
);

CREATE TABLE projects (
  id VARCHAR(128) PRIMARY KEY,
  name VARCHAR(255) NOT NULL UNIQUE,
  access_strategy mappo_project_access_strategy NOT NULL,
  deployment_driver mappo_project_deployment_driver NOT NULL,
  release_artifact_source mappo_project_release_artifact_source NOT NULL,
  runtime_health_provider mappo_project_runtime_health_provider NOT NULL,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

INSERT INTO projects (
  id,
  name,
  access_strategy,
  deployment_driver,
  release_artifact_source,
  runtime_health_provider
) VALUES
  (
    'azure-managed-app-deployment-stack',
    'Azure Managed App Deployment Stack',
    'azure_workload_rbac',
    'azure_deployment_stack',
    'blob_arm_template',
    'azure_container_app_http'
  ),
  (
    'azure-managed-app-template-spec',
    'Azure Managed App Template Spec',
    'azure_workload_rbac',
    'azure_template_spec',
    'template_spec_resource',
    'azure_container_app_http'
  );

ALTER TABLE releases ADD COLUMN project_id VARCHAR(128);
ALTER TABLE targets ADD COLUMN project_id VARCHAR(128);
ALTER TABLE runs ADD COLUMN project_id VARCHAR(128);

UPDATE releases
SET project_id = CASE
  WHEN source_type = 'template_spec' THEN 'azure-managed-app-template-spec'
  ELSE 'azure-managed-app-deployment-stack'
END;

UPDATE targets
SET project_id = 'azure-managed-app-deployment-stack';

UPDATE runs
SET project_id = releases.project_id
FROM releases
WHERE runs.release_id = releases.id;

ALTER TABLE releases
  ALTER COLUMN project_id SET NOT NULL,
  ADD CONSTRAINT fk_releases_project_id
    FOREIGN KEY (project_id) REFERENCES projects (id);

ALTER TABLE targets
  ALTER COLUMN project_id SET NOT NULL,
  ADD CONSTRAINT fk_targets_project_id
    FOREIGN KEY (project_id) REFERENCES projects (id);

ALTER TABLE runs
  ALTER COLUMN project_id SET NOT NULL,
  ADD CONSTRAINT fk_runs_project_id
    FOREIGN KEY (project_id) REFERENCES projects (id);

CREATE INDEX idx_releases_project_id
  ON releases (project_id);

CREATE INDEX idx_targets_project_id
  ON targets (project_id);

CREATE INDEX idx_runs_project_id
  ON runs (project_id);

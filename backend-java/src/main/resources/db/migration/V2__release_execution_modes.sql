CREATE TYPE mappo_deployment_mode AS ENUM ('container_patch', 'template_spec');
CREATE TYPE mappo_deployment_scope AS ENUM ('resource_group', 'subscription');

ALTER TABLE releases
  ADD COLUMN deployment_mode mappo_deployment_mode NOT NULL DEFAULT 'container_patch',
  ADD COLUMN template_spec_version_id VARCHAR(2048) NULL,
  ADD COLUMN deployment_scope mappo_deployment_scope NOT NULL DEFAULT 'resource_group',
  ADD COLUMN deployment_mode_settings JSONB NOT NULL DEFAULT '{}'::jsonb;

ALTER TABLE runs
  ADD COLUMN execution_mode mappo_deployment_mode NOT NULL DEFAULT 'container_patch';

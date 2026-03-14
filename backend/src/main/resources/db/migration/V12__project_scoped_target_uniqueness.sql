ALTER TABLE targets
  DROP CONSTRAINT IF EXISTS targets_tenant_id_subscription_id_key;

ALTER TABLE targets
  ADD CONSTRAINT uq_targets_project_tenant_subscription
    UNIQUE (project_id, tenant_id, subscription_id);

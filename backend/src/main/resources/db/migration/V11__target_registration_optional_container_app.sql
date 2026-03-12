ALTER TABLE target_registrations
  ALTER COLUMN managed_resource_group_id DROP NOT NULL;

ALTER TABLE target_registrations
  ALTER COLUMN container_app_resource_id DROP NOT NULL;

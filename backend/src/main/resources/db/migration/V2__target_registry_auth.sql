CREATE TYPE mappo_registry_auth_mode AS ENUM (
  'none',
  'shared_service_principal_secret',
  'customer_managed_secret'
);

ALTER TABLE target_registrations
  ADD COLUMN deployment_stack_name VARCHAR(128) NULL,
  ADD COLUMN registry_auth_mode mappo_registry_auth_mode NULL,
  ADD COLUMN registry_server VARCHAR(255) NULL,
  ADD COLUMN registry_username VARCHAR(255) NULL,
  ADD COLUMN registry_password_secret_name VARCHAR(255) NULL;

-- flyway:executeInTransaction=false

ALTER TYPE mappo_release_source_type
  ADD VALUE IF NOT EXISTS 'external_deployment_inputs';

CREATE TABLE IF NOT EXISTS release_external_input_entries (
  release_id VARCHAR(128) NOT NULL REFERENCES releases (id) ON DELETE CASCADE,
  input_key VARCHAR(128) NOT NULL,
  input_value VARCHAR(4096) NOT NULL,
  PRIMARY KEY (release_id, input_key)
);

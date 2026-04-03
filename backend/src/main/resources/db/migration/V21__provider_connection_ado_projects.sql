CREATE TABLE provider_connection_ado_projects (
  connection_id VARCHAR(128) NOT NULL
    REFERENCES provider_connections (id) ON DELETE CASCADE,
  project_id VARCHAR(255) NOT NULL,
  project_name VARCHAR(255) NOT NULL,
  web_url VARCHAR(1024),
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  PRIMARY KEY (connection_id, project_id)
);

CREATE INDEX idx_provider_connection_ado_projects_connection_id
  ON provider_connection_ado_projects (connection_id);

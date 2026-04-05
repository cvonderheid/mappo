ALTER TABLE projects
  ADD COLUMN theme_key VARCHAR(64);

UPDATE projects
SET theme_key = 'harbor-teal'
WHERE id = 'azure-managed-app-deployment-stack'
  AND (theme_key IS NULL OR btrim(theme_key) = '');

UPDATE projects
SET theme_key = 'vectr-signal'
WHERE id = 'azure-managed-app-template-spec'
  AND (theme_key IS NULL OR btrim(theme_key) = '');

UPDATE projects
SET theme_key = 'scalr-slate'
WHERE id = 'azure-appservice-ado-pipeline'
  AND (theme_key IS NULL OR btrim(theme_key) = '');

CREATE INDEX idx_projects_theme_key
  ON projects (theme_key);

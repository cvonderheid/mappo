ALTER TABLE marketplace_events
  ADD COLUMN project_id VARCHAR(128);

UPDATE marketplace_events events
SET project_id = targets.project_id
FROM targets
WHERE events.target_id = targets.id
  AND events.project_id IS NULL;

CREATE INDEX idx_marketplace_events_project_id_created_at
  ON marketplace_events (project_id, created_at DESC);

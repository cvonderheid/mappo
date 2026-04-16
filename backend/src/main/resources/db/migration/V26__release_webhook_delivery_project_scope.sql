ALTER TABLE release_webhook_deliveries
  ADD COLUMN IF NOT EXISTS project_ids_text TEXT NULL;

UPDATE release_webhook_deliveries delivery
SET project_ids_text = scoped.project_ids_text
FROM (
  SELECT
    delivery.id,
    string_agg(DISTINCT releases.project_id, E'\n' ORDER BY releases.project_id) AS project_ids_text
  FROM release_webhook_deliveries delivery
  JOIN releases
    ON position(E'\n' || releases.id || E'\n' IN E'\n' || coalesce(delivery.created_release_ids_text, '') || E'\n') > 0
  GROUP BY delivery.id
) scoped
WHERE delivery.id = scoped.id;

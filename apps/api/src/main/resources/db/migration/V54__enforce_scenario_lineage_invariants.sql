-- Repair any histories created before lineage-scoped locking existed. Ordering
-- is deterministic and preserves the relative order of every authored row.
WITH ranked_revisions AS (
    SELECT id,
           ROW_NUMBER() OVER (
               PARTITION BY scenario_lineage_id
               ORDER BY content_version, created_at, id
           ) AS repaired_version
    FROM scenarios
)
UPDATE scenarios scenario
SET content_version = ranked.repaired_version
FROM ranked_revisions ranked
WHERE scenario.id = ranked.id
  AND scenario.content_version <> ranked.repaired_version;

-- If historical races published multiple rows, retain the newest authored
-- revision and archive the rest before enforcing the catalogue invariant.
WITH ranked_active AS (
    SELECT id,
           ROW_NUMBER() OVER (
               PARTITION BY scenario_lineage_id
               ORDER BY content_version DESC, updated_at DESC, id DESC
           ) AS active_rank
    FROM scenarios
    WHERE status = 'ACTIVE'
)
UPDATE scenarios scenario
SET status = 'ARCHIVED', updated_at = CURRENT_TIMESTAMP
FROM ranked_active ranked
WHERE scenario.id = ranked.id
  AND ranked.active_rank > 1;

ALTER TABLE scenarios
    ADD CONSTRAINT uq_scenarios_lineage_content_version
    UNIQUE (scenario_lineage_id, content_version);

CREATE UNIQUE INDEX uq_scenarios_one_active_per_lineage
    ON scenarios (scenario_lineage_id)
    WHERE status = 'ACTIVE';

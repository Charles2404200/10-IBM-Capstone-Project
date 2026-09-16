-- Research evidence predates the engagement foreign key. Remove any legacy
-- orphaned rows, then make the relationship explicit for account deletion.
DELETE FROM research_evidence evidence
WHERE NOT EXISTS (
    SELECT 1 FROM engagements engagement WHERE engagement.id = evidence.engagement_id
);

ALTER TABLE research_evidence DROP CONSTRAINT IF EXISTS research_evidence_engagement_id_fkey;
ALTER TABLE research_evidence ADD CONSTRAINT research_evidence_engagement_id_fkey
    FOREIGN KEY (engagement_id) REFERENCES engagements(id) ON DELETE CASCADE;

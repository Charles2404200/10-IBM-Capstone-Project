ALTER TABLE research_evidence
    ADD COLUMN origin VARCHAR(30) NOT NULL DEFAULT 'USER_SUPPLIED',
    ADD COLUMN verification_status VARCHAR(30) NOT NULL DEFAULT 'UNVERIFIED';

UPDATE research_evidence
SET origin = CASE
    WHEN source_title IS NULL OR source_title = '' THEN 'USER_SUPPLIED'
    ELSE 'SCENARIO_CURATED'
END;

UPDATE research_evidence
SET verification_status = CASE
    WHEN origin = 'USER_SUPPLIED' THEN 'UNVERIFIED'
    ELSE 'CORROBORATED'
END;

ALTER TABLE meetings
    ADD COLUMN completion_outcome VARCHAR(20),
    ADD COLUMN debrief_feedback TEXT;

CREATE TABLE meeting_debrief_tips (
    meeting_id UUID NOT NULL REFERENCES meetings(id) ON DELETE CASCADE,
    tip_order INTEGER NOT NULL,
    tip TEXT,
    PRIMARY KEY (meeting_id, tip_order)
);

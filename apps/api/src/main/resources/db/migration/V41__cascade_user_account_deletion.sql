-- An administrator may permanently remove an account. Learner-owned records
-- are deleted with the account, keeping retention and foreign-key behaviour explicit.
ALTER TABLE engagements DROP CONSTRAINT IF EXISTS engagements_user_id_fkey;
ALTER TABLE engagements ADD CONSTRAINT engagements_user_id_fkey
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE;

ALTER TABLE user_achievements DROP CONSTRAINT IF EXISTS user_achievements_user_id_fkey;
ALTER TABLE user_achievements ADD CONSTRAINT user_achievements_user_id_fkey
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE;

ALTER TABLE engagement_events DROP CONSTRAINT IF EXISTS engagement_events_engagement_id_fkey;
ALTER TABLE engagement_events ADD CONSTRAINT engagement_events_engagement_id_fkey
    FOREIGN KEY (engagement_id) REFERENCES engagements(id) ON DELETE CASCADE;

ALTER TABLE outreach_attempts DROP CONSTRAINT IF EXISTS outreach_attempts_engagement_id_fkey;
ALTER TABLE outreach_attempts ADD CONSTRAINT outreach_attempts_engagement_id_fkey
    FOREIGN KEY (engagement_id) REFERENCES engagements(id) ON DELETE CASCADE;

ALTER TABLE meeting_preparations DROP CONSTRAINT IF EXISTS meeting_preparations_engagement_id_fkey;
ALTER TABLE meeting_preparations ADD CONSTRAINT meeting_preparations_engagement_id_fkey
    FOREIGN KEY (engagement_id) REFERENCES engagements(id) ON DELETE CASCADE;

ALTER TABLE meetings DROP CONSTRAINT IF EXISTS meetings_engagement_id_fkey;
ALTER TABLE meetings ADD CONSTRAINT meetings_engagement_id_fkey
    FOREIGN KEY (engagement_id) REFERENCES engagements(id) ON DELETE CASCADE;

ALTER TABLE conversation_turns DROP CONSTRAINT IF EXISTS conversation_turns_meeting_id_fkey;
ALTER TABLE conversation_turns ADD CONSTRAINT conversation_turns_meeting_id_fkey
    FOREIGN KEY (meeting_id) REFERENCES meetings(id) ON DELETE CASCADE;

ALTER TABLE persona_states DROP CONSTRAINT IF EXISTS persona_states_engagement_id_fkey;
ALTER TABLE persona_states ADD CONSTRAINT persona_states_engagement_id_fkey
    FOREIGN KEY (engagement_id) REFERENCES engagements(id) ON DELETE CASCADE;

ALTER TABLE proposals DROP CONSTRAINT IF EXISTS proposals_engagement_id_fkey;
ALTER TABLE proposals ADD CONSTRAINT proposals_engagement_id_fkey
    FOREIGN KEY (engagement_id) REFERENCES engagements(id) ON DELETE CASCADE;

ALTER TABLE assessments DROP CONSTRAINT IF EXISTS assessments_engagement_id_fkey;
ALTER TABLE assessments ADD CONSTRAINT assessments_engagement_id_fkey
    FOREIGN KEY (engagement_id) REFERENCES engagements(id) ON DELETE CASCADE;

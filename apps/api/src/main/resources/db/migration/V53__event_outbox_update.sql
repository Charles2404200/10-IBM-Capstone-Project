ALTER TABLE event_outbox
    ADD COLUMN claim_token UUID;

CREATE INDEX idx_event_outbox_expired_leases
    ON event_outbox(status);
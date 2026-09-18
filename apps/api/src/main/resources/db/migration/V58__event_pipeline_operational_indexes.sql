-- Partial indexes keep the hot outbox paths small as published history grows.
CREATE INDEX idx_event_outbox_processing_lease
    ON event_outbox (processing_started_at)
    WHERE status = 'PROCESSING';

CREATE INDEX idx_event_outbox_ordering_blockers
    ON event_outbox (ordering_key, sequence_number)
    WHERE ordering_mode = 'ORDERED' AND status <> 'PUBLISHED';

CREATE INDEX idx_event_outbox_published_cleanup
    ON event_outbox (published_at, id)
    WHERE status = 'PUBLISHED';

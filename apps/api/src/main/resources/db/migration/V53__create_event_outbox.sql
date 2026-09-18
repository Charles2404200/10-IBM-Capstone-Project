CREATE TABLE event_outbox (
    id                    UUID PRIMARY KEY,
    topic                 VARCHAR(255) NOT NULL,
    event_type            VARCHAR(255) NOT NULL,
    schema_version        INTEGER NOT NULL,
    ordering_mode         VARCHAR(30) NOT NULL,
    ordering_key          VARCHAR(255),
    sequence_number       BIGINT,
    payload               TEXT NOT NULL,
    status                VARCHAR(30) NOT NULL,
    attempt_count         INTEGER NOT NULL DEFAULT 0,
    processing_started_at TIMESTAMPTZ,
    next_attempt_at       TIMESTAMPTZ,
    published_at          TIMESTAMPTZ,
    created_at            TIMESTAMPTZ NOT NULL,
    updated_at            TIMESTAMPTZ NOT NULL,
    version               BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uk_outbox_ordering_sequence
        UNIQUE (ordering_key, sequence_number),
    CONSTRAINT ck_event_outbox_ordering
        CHECK (
            (ordering_mode = 'UNORDERED' AND ordering_key IS NULL AND sequence_number IS NULL)
            OR
            (ordering_mode = 'ORDERED' AND ordering_key IS NOT NULL AND sequence_number IS NOT NULL)
        )
);

CREATE INDEX idx_event_outbox_dispatch
    ON event_outbox (status, next_attempt_at, created_at);

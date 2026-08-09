CREATE TABLE meeting_response_option_sets (
    id              UUID PRIMARY KEY,
    meeting_id      UUID NOT NULL REFERENCES meetings(id) ON DELETE CASCADE,
    source_sequence INT NOT NULL,
    created_at      TIMESTAMPTZ NOT NULL,
    updated_at      TIMESTAMPTZ NOT NULL,
    version         BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uk_meeting_response_options_source UNIQUE (meeting_id, source_sequence)
);

CREATE TABLE meeting_response_option_values (
    option_set_id   UUID NOT NULL REFERENCES meeting_response_option_sets(id) ON DELETE CASCADE,
    option_position INT NOT NULL,
    content         TEXT NOT NULL,
    PRIMARY KEY (option_set_id, option_position)
);

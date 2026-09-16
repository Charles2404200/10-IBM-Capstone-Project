CREATE TABLE event_sequence
(
    ordering_key  VARCHAR(255) PRIMARY KEY,
    current_value BIGINT NOT NULL
);

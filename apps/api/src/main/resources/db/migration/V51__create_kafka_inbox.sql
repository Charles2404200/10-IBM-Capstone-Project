-- V38__create_kafka_inbox.sql

CREATE TABLE kafka_inbox
(
    consumer_group VARCHAR(200) NOT NULL,
    event_id UUID NOT NULL,

    event_type VARCHAR(150) NOT NULL,

    topic VARCHAR(200) NOT NULL,
    partition_no INTEGER NOT NULL,
    offset_no BIGINT NOT NULL,

    processed_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    PRIMARY KEY (
        consumer_group,
        event_id
    )
);

CREATE INDEX idx_kafka_inbox_processed_at
    ON kafka_inbox(processed_at);
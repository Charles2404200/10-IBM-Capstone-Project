package com.ibm.consulting.sim.shared.domain.kafka;

//CREATE TABLE kafka_inbox
//        (
//                consumer_group VARCHAR(200) NOT NULL,
//event_id UUID NOT NULL,
//
//event_type VARCHAR(150) NOT NULL,
//
//topic VARCHAR(200) NOT NULL,
//partition_no INTEGER NOT NULL,
//offset_no BIGINT NOT NULL,
//
//processed_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
//
//PRIMARY KEY (
//        consumer_group,
//        event_id
//        )
//);
//
//CREATE INDEX idx_kafka_inbox_processed_at
//ON kafka_inbox(processed_at);

import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(
        name = "kafka_inbox",
        indexes = {
                @Index(
                        name = "idx_kafka_inbox_processed_at",
                        columnList = "processed_at"
                )
        }
)
public class KafkaInboxEntity {

    @EmbeddedId
    private KafkaInboxId id;

    @Column(name = "event_type", nullable = false, length = 150)
    private String eventType;

    @Column(name = "topic", nullable = false, length = 200)
    private String topic;

    @Column(name = "partition_no", nullable = false)
    private int partitionNo;

    @Column(name = "offset_no", nullable = false)
    private long offsetNo;

    @Column(name = "processed_at", nullable = false)
    private Instant processedAt;

    protected KafkaInboxEntity() {
    }

    public KafkaInboxEntity(
            String consumerGroup,
            UUID eventId,
            String eventType,
            String topic,
            int partitionNo,
            long offsetNo
    ) {
        this.id = new KafkaInboxId(
                consumerGroup,
                eventId
        );

        this.eventType = eventType;
        this.topic = topic;
        this.partitionNo = partitionNo;
        this.offsetNo = offsetNo;
        this.processedAt = Instant.now();
    }

    public String getConsumerGroup() {
        return id.getConsumerGroup();
    }

    public UUID getEventId() {
        return id.getEventId();
    }

    public String getEventType() {
        return eventType;
    }

    public String getTopic() {
        return topic;
    }

    public int getPartitionNo() {
        return partitionNo;
    }

    public long getOffsetNo() {
        return offsetNo;
    }

    public Instant getProcessedAt() {
        return processedAt;
    }
}

package com.ibm.consulting.sim.shared.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(
        name = "event_outbox",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_outbox_ordering_sequence",
                        columnNames = {
                                "ordering_key",
                                "sequence_number"
                        }
                )
        }
)
public class OutboxEvent extends BaseEntity {

    @Column(nullable = false)
    private String topic;

    @Column(nullable = false)
    private String eventType;

    @Column(nullable = false)
    private int schemaVersion;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private OrderingMode orderingMode;

    private String orderingKey;

    private Long sequenceNumber;

    @Column(
            nullable = false,
            columnDefinition = "text"
    )
    private String payload;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private OutboxStatus status;

    @Column(nullable = false)
    private int attemptCount;

    private Instant processingStartedAt;

    private Instant nextAttemptAt;

    private Instant publishedAt;

    /*
     * Required by JPA.
     *
     * protected is sufficient because application
     * code should create OutboxEvent through the
     * ordered() / unordered() factory methods.
     */
    protected OutboxEvent() {
    }

    private OutboxEvent(UUID id) {
        super(id);
    }

    public static OutboxEvent unordered(
            UUID id,
            String topic,
            String eventType,
            int schemaVersion,
            String payload
    ) {

        OutboxEvent event =
                new OutboxEvent(id);

        event.topic = topic;
        event.eventType = eventType;
        event.schemaVersion = schemaVersion;

        event.orderingMode =
                OrderingMode.UNORDERED;

        event.payload = payload;

        event.status =
                OutboxStatus.PENDING;

        event.attemptCount = 0;

        return event;
    }

    public static OutboxEvent ordered(
            UUID id,
            String topic,
            String eventType,
            int schemaVersion,
            String orderingKey,
            long sequence,
            String payload
    ) {

        OutboxEvent event =
                new OutboxEvent(id);

        event.topic = topic;
        event.eventType = eventType;
        event.schemaVersion = schemaVersion;

        event.orderingMode =
                OrderingMode.ORDERED;

        event.orderingKey =
                orderingKey;

        event.sequenceNumber =
                sequence;

        event.payload =
                payload;

        event.status =
                OutboxStatus.PENDING;

        event.attemptCount = 0;


        return event;
    }

    public EventEnvelope toEnvelope() {

        return new EventEnvelope(
                getId(),
                eventType,
                schemaVersion,
                orderingMode,
                orderingKey,
                sequenceNumber,
                getCreatedAt(),
                payload
        );
    }

    public void markProcessing() {

        requireStatus(OutboxStatus.PENDING, "mark processing");

        this.status =
                OutboxStatus.PROCESSING;

        this.processingStartedAt =
                Instant.now();
    }

    public void markPublished() {

        requireStatus(OutboxStatus.PROCESSING, "mark published");

        this.status =
                OutboxStatus.PUBLISHED;

        this.publishedAt =
                Instant.now();

        this.processingStartedAt = null;

        this.nextAttemptAt = null;
    }

    public void markRetry(
            Duration delay
    ) {

        requireStatus(OutboxStatus.PROCESSING, "mark retry");
        Objects.requireNonNull(delay, "delay must not be null");
        if (delay.isNegative()) {
            throw new IllegalArgumentException("delay must not be negative");
        }

        this.status =
                OutboxStatus.PENDING;

        this.attemptCount++;

        this.processingStartedAt = null;

        this.nextAttemptAt =
                Instant.now().plus(delay);
    }

    private void requireStatus(OutboxStatus requiredStatus, String transition) {
        if (status != requiredStatus) {
            throw new IllegalStateException(
                    "Cannot " + transition + " outbox event from status " + status
            );
        }
    }

    public String getTopic() {
        return topic;
    }

    public String getEventType() {
        return eventType;
    }

    public int getSchemaVersion() {
        return schemaVersion;
    }

    public OrderingMode getOrderingMode() {
        return orderingMode;
    }

    public String getOrderingKey() {
        return orderingKey;
    }

    public Long getSequenceNumber() {
        return sequenceNumber;
    }

    public String getPayload() {
        return payload;
    }

    public OutboxStatus getStatus() {
        return status;
    }

    public int getAttemptCount() {
        return attemptCount;
    }

    public Instant getProcessingStartedAt() {
        return processingStartedAt;
    }

    public Instant getNextAttemptAt() {
        return nextAttemptAt;
    }

    public Instant getPublishedAt() {
        return publishedAt;
    }
}

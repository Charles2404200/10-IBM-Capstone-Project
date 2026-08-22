package com.ibm.consulting.sim.shared.domain;

import jakarta.persistence.*;

import java.time.Instant;

@Entity
@Table(name = "event_outbox")
public class OutboxEvent extends BaseEntity {

    @Column(nullable = false)
    private String eventType;

    @Column(nullable = false)
    private String topic;

    @Column(nullable = false)
    private String dest;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private OrderingMode orderingMode;

    /**
     * orderId, accountId, conversationId, etc.
     *
     * null when ordering does not matter.
     */
    private String orderingKey;

    /**
     * 1, 2, 3...
     *
     * null for unordered events.
     */
    private int sequenceNumber;

    @Column(nullable = false, columnDefinition = "text")
    private String payload;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private OutboxStatus status;

    private Instant publishedAt;

    private Instant processingStartedAt;

    //is effectively treated by Java as:
    //
    //protected OutboxEvent() {
    //    super();
    //}
    protected OutboxEvent() {
    }

    public static OutboxEvent ordered(
            String eventType,
            String orderingKey,
            int sequence,
            String payload,
            String topic,
            String dest
    ) {
        OutboxEvent event = new OutboxEvent();

        event.eventType = eventType;
        event.orderingMode = OrderingMode.ORDERED;
        event.orderingKey = orderingKey;
        event.sequenceNumber = sequence;
        event.payload = payload;
        event.status = OutboxStatus.PENDING;
        event.topic = topic;
        event.dest = dest;

        return event;
    }

    public static OutboxEvent unordered(
            String eventType,
            String payload,
            String topic,
            String dest
    ) {
        // this is used for calling the
        // constructor of the super class
        OutboxEvent event = new OutboxEvent();

        event.eventType = eventType;
        event.orderingMode = OrderingMode.UNORDERED;
        event.payload = payload;
        event.status = OutboxStatus.PENDING;
        event.topic = topic;
        event.dest = dest;

        return event;
    }

    public void markProcessing() {
        this.status = OutboxStatus.PROCESSING;
        this.processingStartedAt = Instant.now();
    }

    public void markPublished() {
        this.status = OutboxStatus.PUBLISHED;
        this.publishedAt = Instant.now();
    }

    public void retry() {
        this.status = OutboxStatus.PENDING;
        this.processingStartedAt = null;
    }

    // getters...

    public String getEventType()
    {
        return this.eventType;
    }

    public String getOrderingKey()
    {
        return this.orderingKey;
    }

    public int getSequenceNumber()
    {
        return this.sequenceNumber;
    }

    public String getPayload()
    {
        return this.payload;
    }

    public String getTopic()
    {
        return topic;
    }

    public String getDest() {
        return this.dest;
    }

    public OrderingMode getOrderingMode()
    {
        return this.orderingMode;
    }
}

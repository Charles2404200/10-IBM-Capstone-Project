package com.ibm.consulting.sim.shared.domain.kafka;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

@Embeddable
public class KafkaInboxId implements Serializable {

    @Column(name = "consumer_group", nullable = false, length = 200)
    private String consumerGroup;

    @Column(name = "event_id", nullable = false)
    private UUID eventId;

    protected KafkaInboxId() {
    }

    public KafkaInboxId(
            String consumerGroup,
            UUID eventId
    ) {
        this.consumerGroup = consumerGroup;
        this.eventId = eventId;
    }

    public String getConsumerGroup() {
        return consumerGroup;
    }

    public UUID getEventId() {
        return eventId;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }

        if (!(o instanceof KafkaInboxId that)) {
            return false;
        }

        return Objects.equals(consumerGroup, that.consumerGroup)
                && Objects.equals(eventId, that.eventId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(
                consumerGroup,
                eventId
        );
    }
}
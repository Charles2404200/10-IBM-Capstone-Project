package com.ibm.consulting.sim.shared.domain.outbox;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "event_sequence")
public class EventSequence {

    @Id
    @Column(name = "ordering_key", nullable = false, updatable = false, length = 255)
    private String orderingKey;

    @Column(name = "current_value", nullable = false)
    private long currentValue;

    protected EventSequence() {
    }

    public String getOrderingKey() {
        return orderingKey;
    }

    public long getCurrentValue() {
        return currentValue;
    }
}

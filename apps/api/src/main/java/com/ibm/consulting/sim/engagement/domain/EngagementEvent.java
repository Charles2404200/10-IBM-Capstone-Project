package com.ibm.consulting.sim.engagement.domain;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

/** Immutable audit record of every engagement state transition. */
@Entity
@Table(name = "engagement_events")
public class EngagementEvent {

    @Id
    @Column(nullable = false, updatable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "engagement_id", nullable = false)
    private Engagement engagement;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private EngagementState state;

    @Column(nullable = false, updatable = false)
    private String description;

    @Column(nullable = false, updatable = false)
    private Instant occurredAt;

    protected EngagementEvent() {}

    static EngagementEvent create(Engagement engagement, EngagementState state, String description) {
        EngagementEvent e = new EngagementEvent();
        e.id = UUID.randomUUID();
        e.engagement = engagement;
        e.state = state;
        e.description = description;
        e.occurredAt = Instant.now();
        return e;
    }

    public UUID getId() { return id; }
    public EngagementState getState() { return state; }
    public String getDescription() { return description; }
    public Instant getOccurredAt() { return occurredAt; }
}

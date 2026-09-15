package com.ibm.consulting.sim.engagement.domain;

import com.ibm.consulting.sim.shared.domain.BaseEntity;
import jakarta.persistence.*;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "engagements")
public class Engagement extends BaseEntity {

    @Column(nullable = false)
    private UUID userId;

    @Column(nullable = false)
    private UUID scenarioId;

    @Column(nullable = false)
    private UUID personaId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private EngagementState state;

    private UUID selectedLeadId;

    private Instant completedAt;

    /** Immutable JSON snapshot of the resolved gameplay profile for this run. */
    @Column(name = "difficulty_profile_snapshot", columnDefinition = "text")
    private String difficultyProfileSnapshot;

    /** Immutable resolved lifecycle for this run; null identifies a legacy run. */
    @Column(name = "lifecycle_definition_snapshot", columnDefinition = "text", updatable = false)
    private String lifecycleDefinitionSnapshot;

    /** Immutable lineage link when this run was restarted after a failed meeting. */
    @Column(name = "retry_of_engagement_id")
    private UUID retryOfEngagementId;

    @OneToMany(mappedBy = "engagement", cascade = CascadeType.ALL, orphanRemoval = true,
            fetch = FetchType.LAZY)
    @OrderBy("occurredAt ASC")
    private List<EngagementEvent> events = new ArrayList<>();

    protected Engagement() {}

    public static Engagement start(UUID userId, UUID scenarioId, UUID personaId) {
        return start(userId, scenarioId, personaId, null);
    }

    public static Engagement start(UUID userId, UUID scenarioId, UUID personaId, String difficultyProfileSnapshot) {
        return start(userId, scenarioId, personaId, difficultyProfileSnapshot, null);
    }

    public static Engagement start(UUID userId, UUID scenarioId, UUID personaId, String difficultyProfileSnapshot,
                                   UUID retryOfEngagementId) {
        return start(userId, scenarioId, personaId, difficultyProfileSnapshot, retryOfEngagementId, null);
    }

    public static Engagement start(UUID userId, UUID scenarioId, UUID personaId, String difficultyProfileSnapshot,
                                   UUID retryOfEngagementId, String lifecycleDefinitionSnapshot) {
        Engagement e = new Engagement();
        e.userId = userId;
        e.scenarioId = scenarioId;
        e.personaId = personaId;
        e.difficultyProfileSnapshot = difficultyProfileSnapshot;
        e.lifecycleDefinitionSnapshot = lifecycleDefinitionSnapshot;
        e.retryOfEngagementId = retryOfEngagementId;
        e.state = EngagementState.QUALIFYING;
        e.recordEvent(retryOfEngagementId == null
                ? "Engagement started"
                : "Retry started from failed engagement: " + retryOfEngagementId);
        return e;
    }

    public void transitionTo(EngagementState newState, String reason) {
        EngagementPolicy.assertValidTransition(this.state, newState);
        this.state = newState;
        if (newState == EngagementState.COMPLETED) {
            this.completedAt = Instant.now();
        }
        recordEvent(reason);
    }

    public void selectLead(UUID leadId) {
        assignLead(leadId);
        transitionTo(EngagementState.CLIENT_INTELLIGENCE, "Lead selected: " + leadId);
    }

    /** Coordinator assigns the command fact before evaluating the lead completion gate. */
    public void assignLead(UUID leadId) {
        EngagementPolicy.assertValidTransition(state, EngagementState.CLIENT_INTELLIGENCE);
        this.selectedLeadId = java.util.Objects.requireNonNull(leadId, "Lead is required");
    }

    /** Restore the pre-command fact if lifecycle validation rejects selection. */
    public void clearSelectedLeadAfterFailedSelection() {
        EngagementPolicy.assertValidTransition(state, EngagementState.CLIENT_INTELLIGENCE);
        this.selectedLeadId = null;
    }

    /** Records a failed attempt without changing the engagement's lifecycle state. */
    public void recordActivity(String description) {
        recordEvent(description);
    }

    private void recordEvent(String description) {
        events.add(EngagementEvent.create(this, state, description));
    }

    public UUID getUserId() { return userId; }
    public UUID getScenarioId() { return scenarioId; }
    public UUID getPersonaId() { return personaId; }
    public EngagementState getState() { return state; }
    public UUID getSelectedLeadId() { return selectedLeadId; }
    public Instant getCompletedAt() { return completedAt; }
    public List<EngagementEvent> getEvents() { return Collections.unmodifiableList(events); }
    public String getDifficultyProfileSnapshot() { return difficultyProfileSnapshot; }
    public String getLifecycleDefinitionSnapshot() { return lifecycleDefinitionSnapshot; }
    public UUID getRetryOfEngagementId() { return retryOfEngagementId; }
}

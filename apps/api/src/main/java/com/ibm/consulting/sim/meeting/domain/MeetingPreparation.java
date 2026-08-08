package com.ibm.consulting.sim.meeting.domain;

import com.ibm.consulting.sim.shared.domain.BaseEntity;
import jakarta.persistence.*;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Learner's pre-meeting planning workspace. Readiness is a derived, deterministic
 * value (see {@link ReadinessPolicy}) — never set directly by the client.
 */
@Entity
@Table(name = "meeting_preparations")
public class MeetingPreparation extends BaseEntity {

    @Column(nullable = false, unique = true)
    private UUID engagementId;

    @Column(columnDefinition = "text")
    private String objective;

    @ElementCollection
    @CollectionTable(name = "meeting_preparation_agenda", joinColumns = @JoinColumn(name = "preparation_id"))
    @Column(name = "item", columnDefinition = "text")
    @OrderColumn(name = "position")
    private List<String> agenda = new ArrayList<>();

    @ElementCollection
    @CollectionTable(name = "meeting_preparation_questions", joinColumns = @JoinColumn(name = "preparation_id"))
    @Column(name = "item", columnDefinition = "text")
    @OrderColumn(name = "position")
    private List<String> discoveryQuestions = new ArrayList<>();

    @Column(nullable = false)
    private int readinessScore;

    protected MeetingPreparation() {}

    public static MeetingPreparation start(UUID engagementId) {
        MeetingPreparation p = new MeetingPreparation();
        p.engagementId = engagementId;
        p.readinessScore = 0;
        return p;
    }

    public void update(String objective, List<String> agenda, List<String> discoveryQuestions) {
        this.objective = objective;
        this.agenda = new ArrayList<>(agenda);
        this.discoveryQuestions = new ArrayList<>(discoveryQuestions);
        this.readinessScore = ReadinessPolicy.calculate(objective, this.agenda, this.discoveryQuestions);
    }

    public boolean isReady() {
        return readinessScore >= ReadinessPolicy.READY_THRESHOLD;
    }

    public UUID getEngagementId() { return engagementId; }
    public String getObjective() { return objective; }
    public List<String> getAgenda() { return List.copyOf(agenda); }
    public List<String> getDiscoveryQuestions() { return List.copyOf(discoveryQuestions); }
    public int getReadinessScore() { return readinessScore; }
}

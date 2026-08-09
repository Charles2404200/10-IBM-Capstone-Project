package com.ibm.consulting.sim.meeting.domain;

import com.ibm.consulting.sim.ai.domain.PersonaStateDelta;
import com.ibm.consulting.sim.scenario.domain.DifficultyProfile;
import com.ibm.consulting.sim.shared.domain.BaseEntity;
import jakarta.persistence.*;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Dynamic client relationship state for a single engagement's meeting (§6.1).
 * Mutations only ever happen through {@link PersonaStateEngine} so that clamping
 * and fact-disclosure rules are applied consistently.
 */
@Entity
@Table(name = "persona_states")
public class PersonaState extends BaseEntity {

    private static final int INITIAL_VALUE = 40;
    private static final int MAX_STARTING_RELATIONSHIP_SCORE = 40;

    @Column(nullable = false, unique = true)
    private UUID engagementId;

    @Column(nullable = false)
    private int trust;

    @Column(nullable = false)
    private int interest;

    @Column(nullable = false)
    private int patience;

    @ElementCollection
    @CollectionTable(name = "persona_state_disclosed_facts", joinColumns = @JoinColumn(name = "persona_state_id"))
    @Column(name = "fact_id")
    private Set<String> disclosedFacts = new LinkedHashSet<>();

    protected PersonaState() {}

    public static PersonaState initial(UUID engagementId) {
        return initial(engagementId, null);
    }

    public static PersonaState initial(UUID engagementId, DifficultyProfile profile) {
        PersonaState s = new PersonaState();
        s.engagementId = engagementId;
        s.trust = initialScore(profile == null ? INITIAL_VALUE : profile.initialTrust());
        s.interest = initialScore(profile == null ? INITIAL_VALUE : profile.initialInterest());
        s.patience = initialScore(profile == null ? INITIAL_VALUE : profile.initialPatience());
        return s;
    }

    void applyClampedDelta(PersonaStateDelta delta) {
        this.trust = clamp(this.trust + delta.trust());
        this.interest = clamp(this.interest + delta.interest());
        this.patience = clamp(this.patience + delta.patience());
    }

    void applyProgressionBoundedDelta(PersonaStateDelta delta, DifficultyProfile profile, int learnerTurnNumber) {
        this.trust = bounded(this.trust + delta.trust(), initialScore(profile.initialTrust()), learnerTurnNumber);
        this.interest = bounded(this.interest + delta.interest(), initialScore(profile.initialInterest()), learnerTurnNumber);
        this.patience = patienceBounded(this.patience + delta.patience(), initialScore(profile.initialPatience()), learnerTurnNumber);
    }

    void disclose(String factId) {
        disclosedFacts.add(factId);
    }

    /** Starts a fresh live-meeting attempt without discarding engagement evidence. */
    public void reset(DifficultyProfile profile) {
        this.trust = initialScore(profile == null ? INITIAL_VALUE : profile.initialTrust());
        this.interest = initialScore(profile == null ? INITIAL_VALUE : profile.initialInterest());
        this.patience = initialScore(profile == null ? INITIAL_VALUE : profile.initialPatience());
        this.disclosedFacts.clear();
    }

    private static int clamp(int value) {
        return Math.max(0, Math.min(100, value));
    }

    private static int initialScore(int configuredValue) {
        return Math.min(MAX_STARTING_RELATIONSHIP_SCORE, clamp(configuredValue));
    }

    private static int bounded(int value, int initialValue, int learnerTurnNumber) {
        return Math.min(clamp(value), MeetingTurnProgressionPolicy.maximumScore(initialValue, learnerTurnNumber));
    }

    private static int patienceBounded(int value, int initialValue, int learnerTurnNumber) {
        return Math.min(clamp(value), MeetingTurnProgressionPolicy.maximumPatienceScore(initialValue, learnerTurnNumber));
    }

    public UUID getEngagementId() { return engagementId; }
    public int getTrust() { return trust; }
    public int getInterest() { return interest; }
    public int getPatience() { return patience; }
    public Set<String> getDisclosedFacts() { return Collections.unmodifiableSet(disclosedFacts); }
}

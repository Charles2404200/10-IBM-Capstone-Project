package com.ibm.consulting.sim.meeting.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

/** JPA representation of one immutable simulation-director decision. */
@Embeddable
public class MeetingBehaviourLedgerEntry {

    @Column(name = "learner_sequence", nullable = false)
    private int learnerSequence;

    @Column(name = "quality", nullable = false, length = 64)
    private String quality;

    @Column(name = "trust_delta", nullable = false)
    private int trustDelta;

    @Column(name = "interest_delta", nullable = false)
    private int interestDelta;

    @Column(name = "patience_delta", nullable = false)
    private int patienceDelta;

    @Column(name = "verified_behaviours", columnDefinition = "text")
    private String verifiedBehaviours;

    @Column(name = "explanation", columnDefinition = "text", nullable = false)
    private String explanation;

    @Column(name = "next_best_action", columnDefinition = "text", nullable = false)
    private String nextBestAction;

    protected MeetingBehaviourLedgerEntry() {
    }

    private MeetingBehaviourLedgerEntry(int learnerSequence, MeetingBehaviourAssessment assessment) {
        this.learnerSequence = learnerSequence;
        this.quality = assessment.quality();
        this.trustDelta = assessment.relationshipDelta().trust();
        this.interestDelta = assessment.relationshipDelta().interest();
        this.patienceDelta = assessment.relationshipDelta().patience();
        this.verifiedBehaviours = String.join(",", assessment.verifiedBehaviours());
        this.explanation = assessment.explanation();
        this.nextBestAction = assessment.nextBestAction();
    }

    public static MeetingBehaviourLedgerEntry from(int learnerSequence, MeetingBehaviourAssessment assessment) {
        return new MeetingBehaviourLedgerEntry(learnerSequence, assessment);
    }

    public int getLearnerSequence() { return learnerSequence; }
    public String getQuality() { return quality; }
    public int getTrustDelta() { return trustDelta; }
    public int getInterestDelta() { return interestDelta; }
    public int getPatienceDelta() { return patienceDelta; }
    public String getVerifiedBehaviours() { return verifiedBehaviours; }
    public String getExplanation() { return explanation; }
    public String getNextBestAction() { return nextBestAction; }
}

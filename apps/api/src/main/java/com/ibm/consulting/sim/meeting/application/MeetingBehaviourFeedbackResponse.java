package com.ibm.consulting.sim.meeting.application;

import com.ibm.consulting.sim.meeting.domain.MeetingBehaviourAssessment;
import com.ibm.consulting.sim.meeting.domain.MeetingBehaviourLedgerEntry;

import java.util.Arrays;
import java.util.List;

/** API-safe explanation of the latest Simulation Director decision. */
public record MeetingBehaviourFeedbackResponse(
        String quality,
        int trustDelta,
        int interestDelta,
        int patienceDelta,
        List<String> verifiedBehaviours,
        String explanation,
        String nextBestAction) {

    public static MeetingBehaviourFeedbackResponse from(MeetingBehaviourAssessment assessment) {
        return new MeetingBehaviourFeedbackResponse(
                assessment.quality(), assessment.relationshipDelta().trust(), assessment.relationshipDelta().interest(),
                assessment.relationshipDelta().patience(), assessment.verifiedBehaviours(), assessment.explanation(),
                assessment.nextBestAction());
    }

    public static MeetingBehaviourFeedbackResponse from(MeetingBehaviourLedgerEntry entry) {
        List<String> behaviours = entry.getVerifiedBehaviours() == null || entry.getVerifiedBehaviours().isBlank()
                ? List.of()
                : Arrays.stream(entry.getVerifiedBehaviours().split(",")).filter(value -> !value.isBlank()).toList();
        return new MeetingBehaviourFeedbackResponse(entry.getQuality(), entry.getTrustDelta(), entry.getInterestDelta(),
                entry.getPatienceDelta(), behaviours, entry.getExplanation(), entry.getNextBestAction());
    }
}

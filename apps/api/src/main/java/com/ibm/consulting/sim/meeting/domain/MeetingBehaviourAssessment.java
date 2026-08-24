package com.ibm.consulting.sim.meeting.domain;

import com.ibm.consulting.sim.ai.domain.PersonaStateDelta;

import java.util.List;

/**
 * Explainable, provider-independent assessment of one learner turn.
 *
 * <p>The simulation director produces this before relationship state mutates.
 * It is persisted with the meeting so learner feedback, trainer review and
 * outcome decisions can all refer to the same durable reasoning.</p>
 */
public record MeetingBehaviourAssessment(
        String quality,
        PersonaStateDelta relationshipDelta,
        List<String> verifiedBehaviours,
        String explanation,
        String nextBestAction) {

    public MeetingBehaviourAssessment {
        relationshipDelta = relationshipDelta == null ? PersonaStateDelta.zero() : relationshipDelta;
        verifiedBehaviours = verifiedBehaviours == null ? List.of() : List.copyOf(verifiedBehaviours);
    }

    public MeetingBehaviourAssessment withRelationshipDelta(PersonaStateDelta appliedDelta) {
        return new MeetingBehaviourAssessment(quality, appliedDelta, verifiedBehaviours, explanation, nextBestAction);
    }
}

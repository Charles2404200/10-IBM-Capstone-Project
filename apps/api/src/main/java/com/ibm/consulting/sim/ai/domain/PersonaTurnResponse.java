package com.ibm.consulting.sim.ai.domain;

import java.util.List;

/**
 * The structured AI turn contract from §5.4 of the implementation plan.
 * Every persona dialogue response must be validated against this shape
 * before any state change is applied.
 */
public record PersonaTurnResponse(
        String spokenResponse,
        List<String> detectedLearnerBehaviours,
        PersonaStateDelta stateDelta,
        List<String> factsDisclosed,
        String objectionRaised,
        List<String> meetingSignals,
        SafetyCheck safety) {

    public record SafetyCheck(boolean allowed, String reason) {}

    public static PersonaTurnResponse safeFallback(String message) {
        return new PersonaTurnResponse(
                message,
                List.of(),
                PersonaStateDelta.zero(),
                List.of(),
                null,
                List.of("fallback_response_used"),
                new SafetyCheck(true, null));
    }
}

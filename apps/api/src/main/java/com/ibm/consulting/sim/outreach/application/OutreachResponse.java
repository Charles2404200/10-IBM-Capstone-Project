package com.ibm.consulting.sim.outreach.application;

import com.ibm.consulting.sim.outreach.domain.OutreachAttempt;

import java.time.Instant;
import java.util.UUID;

public record OutreachResponse(
        UUID id,
        UUID engagementId,
        int attemptNumber,
        String subject,
        String body,
        String clientReply,
        String outcome,
        Integer scorePersonalisation,
        Integer scoreRelevance,
        Integer scoreClarity,
        Integer scoreCallToAction,
        Instant createdAt) {

    public static OutreachResponse from(OutreachAttempt a) {
        return new OutreachResponse(a.getId(), a.getEngagementId(), a.getAttemptNumber(),
                a.getSubject(), a.getBody(), a.getClientReply(), a.getOutcome().name(),
                a.getScorePersonalisation(), a.getScoreRelevance(),
                a.getScoreClarity(), a.getScoreCallToAction(), a.getCreatedAt());
    }
}

package com.ibm.consulting.sim.outreach.application;

import com.ibm.consulting.sim.outreach.domain.OutreachAttempt;
import com.ibm.consulting.sim.outreach.domain.OutreachRequestDetails;
import com.ibm.consulting.sim.outreach.domain.OutreachRequestPolicy;

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
        String nextAction,
        String requestTitle,
        String requestSummary,
        java.util.List<String> requestRequirements,
        Instant createdAt) {

    public static OutreachResponse from(OutreachAttempt a) {
        OutreachRequestDetails request = OutreachRequestPolicy.detailsFor(
                a.getOutcome(), a.getClientReply(), a.getNextAction());
        return new OutreachResponse(a.getId(), a.getEngagementId(), a.getAttemptNumber(),
                a.getSubject(), a.getBody(), a.getClientReply(), a.getOutcome().name(),
                a.getScorePersonalisation(), a.getScoreRelevance(),
                a.getScoreClarity(), a.getScoreCallToAction(), request.nextAction().name(),
                request.title(), request.summary(), request.requirements(), a.getCreatedAt());
    }
}

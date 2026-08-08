package com.ibm.consulting.sim.outreach.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ibm.consulting.sim.ai.application.AiOrchestrationService;
import com.ibm.consulting.sim.ai.domain.OutreachEvaluationResult;
import com.ibm.consulting.sim.ai.infrastructure.OutreachEvaluationParser;
import com.ibm.consulting.sim.engagement.domain.Engagement;
import com.ibm.consulting.sim.engagement.domain.EngagementRepository;
import com.ibm.consulting.sim.engagement.domain.EngagementState;
import com.ibm.consulting.sim.outreach.domain.OutreachAttempt;
import com.ibm.consulting.sim.outreach.domain.OutreachOutcome;
import com.ibm.consulting.sim.outreach.domain.OutreachRepository;
import com.ibm.consulting.sim.shared.domain.DomainException;
import com.ibm.consulting.sim.shared.domain.NotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class OutreachService {

    private static final int MAX_ATTEMPTS = 3;
    private static final int PROMPT_VERSION = 1;

    private final OutreachRepository outreachRepository;
    private final EngagementRepository engagementRepository;
    private final AiOrchestrationService aiOrchestrationService;
    private final OutreachEvaluationParser parser;

    public OutreachService(OutreachRepository outreachRepository,
                           EngagementRepository engagementRepository,
                           AiOrchestrationService aiOrchestrationService,
                           ObjectMapper objectMapper) {
        this.outreachRepository = outreachRepository;
        this.engagementRepository = engagementRepository;
        this.aiOrchestrationService = aiOrchestrationService;
        this.parser = new OutreachEvaluationParser(objectMapper);
    }

    @Transactional
    public OutreachResponse send(UUID engagementId, UUID userId, String subject, String body) {
        Engagement engagement = engagementRepository.findByIdAndUserId(engagementId, userId)
                .orElseThrow(() -> new NotFoundException("Engagement", engagementId));

        if (engagement.getState() != EngagementState.HYPOTHESIS_READY
                && engagement.getState() != EngagementState.OUTREACHING) {
            throw new InvalidOutreachStateException(engagement.getState());
        }

        int attemptCount = outreachRepository.countByEngagementId(engagementId);
        if (attemptCount >= MAX_ATTEMPTS) {
            throw new MaxOutreachAttemptsException();
        }

        // Transition to in-progress
        if (engagement.getState() == EngagementState.HYPOTHESIS_READY) {
            engagement.transitionTo(EngagementState.OUTREACHING, "Outreach attempt #" + (attemptCount + 1));
        }

        OutreachAttempt attempt = OutreachAttempt.create(engagementId, attemptCount + 1, subject, body);

        OutreachEvaluationResult evaluation = aiOrchestrationService.execute(
                "outreach_evaluation",
                engagementId,
                buildPrompt(subject, body),
                PROMPT_VERSION,
                parser,
                OutreachEvaluationResult::safeFallback);

        OutreachOutcome outcome = OutreachOutcome.valueOf(evaluation.outcome());
        attempt.resolve(evaluation.clientReply(), outcome,
                evaluation.personalisation(), evaluation.relevance(),
                evaluation.clarity(), evaluation.callToAction());

        outreachRepository.save(attempt);

        // Transition engagement based on outcome
        EngagementState nextState = outcome == OutreachOutcome.ACCEPTED
                ? EngagementState.MEETING_SECURED
                : EngagementState.OUTREACHING;
        engagement.transitionTo(nextState, "Outreach outcome: " + outcome);
        engagementRepository.save(engagement);

        return OutreachResponse.from(attempt);
    }

    private String buildPrompt(String subject, String body) {
        return """
                You are evaluating a cold outreach email from a trainee consultant to a prospective client.
                Assess personalisation, relevance, clarity and call-to-action strength, then write a realistic
                client reply. Return ONLY JSON matching:
                {"clientReply": string, "outcome": "ACCEPTED"|"FOLLOW_UP_REQUIRED"|"REJECTED",
                 "scores": {"personalisation": 0-100, "relevance": 0-100, "clarity": 0-100, "callToAction": 0-100},
                 "reasonCodes": string[], "relationshipStateDelta": {"trust": int, "interest": int}}

                Subject: %s
                Body: %s
                """.formatted(subject, body);
    }

    @Transactional(readOnly = true)
    public List<OutreachResponse> listAttempts(UUID engagementId, UUID userId) {
        engagementRepository.findByIdAndUserId(engagementId, userId)
                .orElseThrow(() -> new NotFoundException("Engagement", engagementId));
        return outreachRepository.findByEngagementId(engagementId).stream()
                .map(OutreachResponse::from)
                .toList();
    }

    public static class InvalidOutreachStateException extends DomainException {
        public InvalidOutreachStateException(EngagementState state) {
            super("Cannot send outreach in state: " + state);
        }
    }

    public static class MaxOutreachAttemptsException extends DomainException {
        public MaxOutreachAttemptsException() {
            super("Maximum outreach attempts (%d) reached".formatted(MAX_ATTEMPTS));
        }
    }
}

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
import com.ibm.consulting.sim.outreach.domain.OutreachNextAction;
import com.ibm.consulting.sim.outreach.domain.OutreachRequestPolicy;
import com.ibm.consulting.sim.outreach.domain.OutreachRepository;
import com.ibm.consulting.sim.outreach.domain.OutreachOutcomePolicy;
import com.ibm.consulting.sim.scenario.application.DifficultyProfileService;
import com.ibm.consulting.sim.scenario.domain.DifficultyProfile;
import com.ibm.consulting.sim.shared.domain.DomainException;
import com.ibm.consulting.sim.shared.domain.NotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Comparator;
import java.util.UUID;

@Service
public class OutreachService {

    private static final int MAX_ATTEMPTS = 3;
    private static final int PROMPT_VERSION = 1;

    private final OutreachRepository outreachRepository;
    private final EngagementRepository engagementRepository;
    private final AiOrchestrationService aiOrchestrationService;
    private final OutreachEvaluationParser parser;
    private final DifficultyProfileService difficultyProfileService;

    public OutreachService(OutreachRepository outreachRepository,
                           EngagementRepository engagementRepository,
                           AiOrchestrationService aiOrchestrationService,
                           ObjectMapper objectMapper,
                           DifficultyProfileService difficultyProfileService) {
        this.outreachRepository = outreachRepository;
        this.engagementRepository = engagementRepository;
        this.aiOrchestrationService = aiOrchestrationService;
        this.parser = new OutreachEvaluationParser(objectMapper);
        this.difficultyProfileService = difficultyProfileService;
    }

    @Transactional
    public OutreachResponse send(UUID engagementId, UUID userId, String subject, String body) {
        Engagement engagement = engagementRepository.findByIdAndUserId(engagementId, userId)
                .orElseThrow(() -> new NotFoundException("Engagement", engagementId));

        if (engagement.getState() != EngagementState.HYPOTHESIS_READY
                && engagement.getState() != EngagementState.OUTREACHING) {
            throw new InvalidOutreachStateException(engagement.getState());
        }

        List<OutreachAttempt> existingAttempts = outreachRepository.findByEngagementId(engagementId);
        existingAttempts.stream()
                .max(Comparator.comparingInt(OutreachAttempt::getAttemptNumber))
                .ifPresent(this::assertFollowUpIsAllowed);

        int attemptCount = existingAttempts.size();
        if (attemptCount >= MAX_ATTEMPTS) {
            throw new MaxOutreachAttemptsException();
        }

        // Transition to in-progress
        if (engagement.getState() == EngagementState.HYPOTHESIS_READY) {
            engagement.transitionTo(EngagementState.OUTREACHING, "Outreach attempt #" + (attemptCount + 1));
        }

        OutreachAttempt attempt = OutreachAttempt.create(engagementId, attemptCount + 1, subject, body);
        DifficultyProfile profile = difficultyProfileService.forEngagement(engagement);

        OutreachEvaluationResult evaluation = aiOrchestrationService.execute(
                "outreach_evaluation",
                engagementId,
                buildPrompt(subject, body, profile),
                PROMPT_VERSION,
                parser,
                OutreachEvaluationResult::safeFallback);

        OutreachOutcome outcome = OutreachOutcomePolicy.decide(evaluation, profile);
        OutreachNextAction nextAction = OutreachRequestPolicy.nextActionFor(outcome, evaluation.clientReply());
        attempt.resolve(evaluation.clientReply(), outcome, nextAction,
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

    private void assertFollowUpIsAllowed(OutreachAttempt latestAttempt) {
        OutreachNextAction requiredAction = OutreachRequestPolicy.detailsFor(
                latestAttempt.getOutcome(), latestAttempt.getClientReply(), latestAttempt.getNextAction()).nextAction();
        if (requiredAction == OutreachNextAction.SUBMIT_CAPABILITY_BRIEF) {
            throw new RequiredOutreachActionException(
                    "The client requested a capability brief. Submit the requested document before sending another email.");
        }
    }

    private String buildPrompt(String subject, String body, DifficultyProfile profile) {
        return """
                You are evaluating a cold outreach email from a trainee consultant to a prospective client.
                Assess personalisation, relevance, clarity and call-to-action strength, then write a realistic
                client reply. Return ONLY JSON matching:
                {"clientReply": string, "outcome": "ACCEPTED"|"FOLLOW_UP_REQUIRED"|"REJECTED",
                 "scores": {"personalisation": 0-100, "relevance": 0-100, "clarity": 0-100, "callToAction": 0-100},
                 "reasonCodes": string[], "relationshipStateDelta": {"trust": int, "interest": int}}

                The deterministic engine requires an average quality score of %d/100 before a meeting can be accepted.
                You may recommend a likely outcome in the JSON, but the backend owns the final state transition.

                Subject: %s
                Body: %s
                """.formatted(profile.outreachAcceptanceThreshold(), subject, body);
    }

    @Transactional(readOnly = true)
    public List<OutreachResponse> listAttempts(UUID engagementId, UUID userId) {
        engagementRepository.findByIdAndUserId(engagementId, userId)
                .orElseThrow(() -> new NotFoundException("Engagement", engagementId));
        return outreachRepository.findByEngagementId(engagementId).stream()
                .sorted(Comparator.comparingInt(OutreachAttempt::getAttemptNumber))
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

    public static class RequiredOutreachActionException extends DomainException {
        RequiredOutreachActionException(String message) {
            super(message);
        }
    }
}

package com.ibm.consulting.sim.assessment.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ibm.consulting.sim.ai.application.AiOrchestrationService;
import com.ibm.consulting.sim.ai.domain.AssessmentFeedback;
import com.ibm.consulting.sim.ai.infrastructure.AssessmentFeedbackParser;
import com.ibm.consulting.sim.assessment.domain.AssessmentRepository;
import com.ibm.consulting.sim.assessment.domain.AssessmentFeedbackStatus;
import com.ibm.consulting.sim.shared.config.CacheConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * Produces the natural-language coaching after the learner-facing assessment
 * has been persisted. This mirrors the proposal decision narrative pattern:
 * deterministic simulation truth remains synchronous; an optional LLM
 * enrichment is bounded, cached and isolated from the request path.
 */
@Component
class AssessmentFeedbackEnricher {
    private static final Logger log = LoggerFactory.getLogger(AssessmentFeedbackEnricher.class);
    private static final int PROMPT_VERSION = 2;

    private final AssessmentRepository assessmentRepository;
    private final AiOrchestrationService aiOrchestrationService;
    private final AssessmentFeedbackParser feedbackParser;
    private final CacheManager cacheManager;

    AssessmentFeedbackEnricher(AssessmentRepository assessmentRepository,
                               AiOrchestrationService aiOrchestrationService,
                               ObjectMapper objectMapper,
                               CacheManager cacheManager) {
        this.assessmentRepository = assessmentRepository;
        this.aiOrchestrationService = aiOrchestrationService;
        this.feedbackParser = new AssessmentFeedbackParser(objectMapper);
        this.cacheManager = cacheManager;
    }

    @Async("assessmentFeedbackExecutor")
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void enrich(AssessmentGeneratedEvent event) {
        AssessmentFeedback resolved;
        try {
            resolved = cachedFeedback(cacheKey(event));
            if (resolved == null) {
                resolved = aiOrchestrationService.execute(
                        "assessment_feedback",
                        event.engagementId(),
                        prompt(event),
                        PROMPT_VERSION,
                        feedbackParser,
                        () -> AssessmentFeedback.safeFallback(event.overallScore()));
                cacheFeedback(cacheKey(event), resolved);
            }
        } catch (RuntimeException enrichmentFailure) {
            log.warn("Assessment coaching enrichment failed for engagement {}. Publishing deterministic fallback.",
                    event.engagementId(), enrichmentFailure);
            resolved = AssessmentFeedback.safeFallback(event.overallScore());
        }

        AssessmentFeedback finalFeedback = resolved;
        assessmentRepository.findByEngagementId(event.engagementId())
                .filter(assessment -> assessment.getFeedbackStatus() == AssessmentFeedbackStatus.PENDING)
                .ifPresent(assessment -> {
                    assessment.replaceFeedback(finalFeedback.feedbackSummary(), finalFeedback.strengths(), finalFeedback.improvementAreas());
                    assessmentRepository.save(assessment);
                });
    }

    private String prompt(AssessmentGeneratedEvent event) {
        String scoreLines = event.competencyScores().stream()
                .map(score -> "- %s: %d/100 (%s)".formatted(
                        score.getCompetencyName(), score.getScore(), score.getEvidenceNote()))
                .reduce("", (left, right) -> left + "\n" + right);

        return """
                You are an IBM consulting coach writing evidence-based feedback for a trainee who just
                completed a training engagement with outcome: %s (overall score %d/100).

                Competency breakdown:%s

                Write concise, encouraging but honest coaching that cites the supplied competency evidence.
                Do not invent client facts, scores, or outcomes. Return ONLY JSON matching:
                {"feedbackSummary": string, "strengths": string[], "improvementAreas": string[]}
                """.formatted(event.outcome(), event.overallScore(), scoreLines);
    }

    private AssessmentFeedback cachedFeedback(String key) {
        Cache cache = cacheManager.getCache(CacheConfig.ASSESSMENT_FEEDBACK_CACHE);
        return cache == null ? null : cache.get(key, AssessmentFeedback.class);
    }

    private void cacheFeedback(String key, AssessmentFeedback feedback) {
        Cache cache = cacheManager.getCache(CacheConfig.ASSESSMENT_FEEDBACK_CACHE);
        if (cache != null) cache.put(key, feedback);
    }

    private String cacheKey(AssessmentGeneratedEvent event) {
        return event.engagementId() + ":" + sha256("v=" + PROMPT_VERSION + "|" + event.outcome()
                + "|" + event.overallScore() + "|" + event.competencyScores());
    }

    private String sha256(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(digest.length * 2);
            for (byte current : digest) hex.append(String.format("%02x", current));
            return hex.toString();
        } catch (NoSuchAlgorithmException unavailable) {
            log.error("SHA-256 is unavailable for assessment feedback cache", unavailable);
            throw new IllegalStateException("SHA-256 is unavailable", unavailable);
        }
    }
}

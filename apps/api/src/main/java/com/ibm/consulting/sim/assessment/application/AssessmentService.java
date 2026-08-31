package com.ibm.consulting.sim.assessment.application;

import com.ibm.consulting.sim.ai.domain.AssessmentFeedback;
import com.ibm.consulting.sim.achievement.application.AchievementEvaluationService;
import com.ibm.consulting.sim.assessment.domain.*;
import com.ibm.consulting.sim.engagement.domain.Engagement;
import com.ibm.consulting.sim.engagement.domain.EngagementRepository;
import com.ibm.consulting.sim.engagement.domain.EngagementState;
import com.ibm.consulting.sim.lead.domain.ResearchEvidenceRepository;
import com.ibm.consulting.sim.meeting.domain.PersonaState;
import com.ibm.consulting.sim.meeting.domain.PersonaStateRepository;
import com.ibm.consulting.sim.outreach.domain.OutreachAttempt;
import com.ibm.consulting.sim.outreach.domain.OutreachRepository;
import com.ibm.consulting.sim.proposal.domain.Proposal;
import com.ibm.consulting.sim.proposal.domain.ClientDecisionOutcome;
import com.ibm.consulting.sim.proposal.domain.ProposalDecision;
import com.ibm.consulting.sim.proposal.domain.ProposalRepository;
import com.ibm.consulting.sim.scenario.domain.Scenario;
import com.ibm.consulting.sim.scenario.domain.ScenarioRepository;
import com.ibm.consulting.sim.shared.domain.DomainException;
import com.ibm.consulting.sim.shared.domain.NotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.context.ApplicationEventPublisher;

import java.util.List;
import java.util.UUID;

/**
 * Generates the final engagement assessment (§4.3 US-09, US-10): deterministic
 * competency scores computed purely from stored engagement data, plus an
 * AI-generated coaching narrative that must cite the same evidence. The AI
 * never determines the scores themselves (§5.2).
 */
@Service
public class AssessmentService {

    private final AssessmentRepository assessmentRepository;
    private final EngagementRepository engagementRepository;
    private final ResearchEvidenceRepository evidenceRepository;
    private final OutreachRepository outreachRepository;
    private final PersonaStateRepository personaStateRepository;
    private final ProposalRepository proposalRepository;
    private final ScenarioRepository scenarioRepository;
    private final AchievementEvaluationService achievementEvaluationService;
    private final ApplicationEventPublisher eventPublisher;

    public AssessmentService(AssessmentRepository assessmentRepository,
                              EngagementRepository engagementRepository,
                              ResearchEvidenceRepository evidenceRepository,
                              OutreachRepository outreachRepository,
                              PersonaStateRepository personaStateRepository,
                              ProposalRepository proposalRepository,
                              ScenarioRepository scenarioRepository,
                              AchievementEvaluationService achievementEvaluationService,
                              ApplicationEventPublisher eventPublisher) {
        this.assessmentRepository = assessmentRepository;
        this.engagementRepository = engagementRepository;
        this.evidenceRepository = evidenceRepository;
        this.outreachRepository = outreachRepository;
        this.personaStateRepository = personaStateRepository;
        this.proposalRepository = proposalRepository;
        this.scenarioRepository = scenarioRepository;
        this.achievementEvaluationService = achievementEvaluationService;
        this.eventPublisher = eventPublisher;
    }

    @Transactional
    public AssessmentResponse generate(UUID engagementId, UUID userId) {
        Engagement engagement = engagementRepository.findByIdAndUserId(engagementId, userId)
                .orElseThrow(() -> new NotFoundException("Engagement", engagementId));

        return assessmentRepository.findByEngagementId(engagementId)
                .map(assessment -> {
                    completeAssessmentLifecycle(engagement, "Recovered completed assessment lifecycle");
                    return AssessmentResponse.from(assessment);
                })
                .orElseGet(() -> buildAndPersist(engagement));
    }

    @Transactional
    public AssessmentResponse get(UUID engagementId, UUID userId) {
        Engagement engagement = engagementRepository.findByIdAndUserId(engagementId, userId)
                .orElseThrow(() -> new NotFoundException("Engagement", engagementId));
        Assessment assessment = assessmentRepository.findByEngagementId(engagementId)
                .orElseThrow(() -> new NotFoundException("Assessment for engagement", engagementId));
        completeAssessmentLifecycle(engagement, "Completed assessment opened");
        return AssessmentResponse.from(assessment);
    }

    /**
     * Reviewer/coach view: unlike {@link #get}, this is not scoped to a specific
     * owning user — access control is enforced at the controller layer via
     * {@code @PreAuthorize} for the {@code REVIEWER}/{@code ADMINISTRATOR} roles.
     */
    @Transactional(readOnly = true)
    public AssessmentResponse getForReview(UUID engagementId) {
        engagementRepository.findById(engagementId)
                .orElseThrow(() -> new NotFoundException("Engagement", engagementId));
        Assessment assessment = assessmentRepository.findByEngagementId(engagementId)
                .orElseThrow(() -> new NotFoundException("Assessment for engagement", engagementId));
        return AssessmentResponse.from(assessment);
    }

    private AssessmentResponse buildAndPersist(Engagement engagement) {
        if (engagement.getState() != EngagementState.CLIENT_DECISION
                && engagement.getState() != EngagementState.REVIEW) {
            throw new AssessmentNotAvailableException(engagement.getState());
        }
        UUID engagementId = engagement.getId();

        int evidenceCount = evidenceRepository.findByEngagementId(engagementId).size();
        int avgOutreachScore = averageOutreachScore(outreachRepository.findByEngagementId(engagementId));
        PersonaState state = personaStateRepository.findByEngagementId(engagementId)
                .orElseGet(() -> PersonaState.initial(engagementId));
        Proposal proposal = proposalRepository.findByEngagementId(engagementId).orElse(null);
        int proposalAlignment = proposal != null
                ? valueOr(proposal.getLearnerPerformanceScore(), proposal.getAlignmentScore())
                : 0;

        List<CompetencyScore> competencyScores = AssessmentEngine.score(
                evidenceCount, avgOutreachScore, state.getTrust(), state.getInterest(), state.getPatience(),
                proposalAlignment);
        Scenario scenario = scenarioRepository.findById(engagement.getScenarioId())
                .orElseThrow(() -> new NotFoundException("Scenario", engagement.getScenarioId()));
        int overallScore = AssessmentEngine.overall(competencyScores, scenario.getRubricWeights());
        String outcome = proposalOutcome(proposal);

        AssessmentFeedback feedback = AssessmentFeedback.pending(overallScore);

        Assessment assessment = Assessment.create(engagementId, competencyScores, overallScore, outcome,
                feedback.feedbackSummary(), feedback.strengths(), feedback.improvementAreas(),
                AssessmentFeedbackStatus.PENDING);
        assessmentRepository.save(assessment);

        achievementEvaluationService.evaluateForUser(engagement.getUserId());
        completeAssessmentLifecycle(engagement, "Assessment generated and portfolio updated");
        eventPublisher.publishEvent(new AssessmentGeneratedEvent(engagementId, competencyScores, overallScore, outcome));

        return AssessmentResponse.from(assessment);
    }

    /**
     * Completes the final two lifecycle steps after a durable assessment exists.
     * This is intentionally idempotent so engagements created before the
     * completion transition was introduced are repaired on their next read.
     */
    private void completeAssessmentLifecycle(Engagement engagement, String reason) {
        boolean changed = false;
        if (engagement.getState() == EngagementState.CLIENT_DECISION) {
            engagement.transitionTo(EngagementState.REVIEW, "Assessment available for review");
            changed = true;
        }
        if (engagement.getState() == EngagementState.REVIEW) {
            engagement.transitionTo(EngagementState.COMPLETED, reason);
            changed = true;
        }
        if (changed) {
            engagementRepository.save(engagement);
        }
    }

    /**
     * Supports engagements created before decision snapshots existed. New
     * proposals retain the richer client outcome; historic proposals preserve
     * their original WON/LOST semantics.
     */
    private String proposalOutcome(Proposal proposal) {
        if (proposal == null) return "PROPOSAL_REJECTED";
        ClientDecisionOutcome clientOutcome = proposal.getClientDecisionOutcome();
        if (clientOutcome != null) return clientOutcome.name();
        return proposal.getDecision() == ProposalDecision.WON ? "PROPOSAL_ACCEPTED" : "PROPOSAL_REJECTED";
    }

    private int valueOr(Integer value, int fallback) {
        return value == null ? fallback : value;
    }

    private int averageOutreachScore(List<OutreachAttempt> attempts) {
        return (int) Math.round(attempts.stream()
                .filter(a -> a.getScorePersonalisation() != null)
                .mapToInt(a -> (a.getScorePersonalisation() + a.getScoreRelevance()
                        + a.getScoreClarity() + a.getScoreCallToAction()) / 4)
                .average()
                .orElse(0));
    }

    public static class AssessmentNotAvailableException extends DomainException {
        public AssessmentNotAvailableException(EngagementState state) {
            super("Assessment is not available in state: " + state);
        }
    }
}

package com.ibm.consulting.sim.assessment.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ibm.consulting.sim.ai.application.AiOrchestrationService;
import com.ibm.consulting.sim.ai.domain.AssessmentFeedback;
import com.ibm.consulting.sim.ai.infrastructure.AssessmentFeedbackParser;
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
import com.ibm.consulting.sim.proposal.domain.ProposalDecision;
import com.ibm.consulting.sim.proposal.domain.ProposalRepository;
import com.ibm.consulting.sim.scenario.domain.Scenario;
import com.ibm.consulting.sim.scenario.domain.ScenarioRepository;
import com.ibm.consulting.sim.shared.domain.DomainException;
import com.ibm.consulting.sim.shared.domain.NotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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

    private static final int PROMPT_VERSION = 1;

    private final AssessmentRepository assessmentRepository;
    private final EngagementRepository engagementRepository;
    private final ResearchEvidenceRepository evidenceRepository;
    private final OutreachRepository outreachRepository;
    private final PersonaStateRepository personaStateRepository;
    private final ProposalRepository proposalRepository;
    private final ScenarioRepository scenarioRepository;
    private final AiOrchestrationService aiOrchestrationService;
    private final AssessmentFeedbackParser feedbackParser;
    private final AchievementEvaluationService achievementEvaluationService;

    public AssessmentService(AssessmentRepository assessmentRepository,
                              EngagementRepository engagementRepository,
                              ResearchEvidenceRepository evidenceRepository,
                              OutreachRepository outreachRepository,
                              PersonaStateRepository personaStateRepository,
                              ProposalRepository proposalRepository,
                              ScenarioRepository scenarioRepository,
                              AiOrchestrationService aiOrchestrationService,
                              ObjectMapper objectMapper,
                              AchievementEvaluationService achievementEvaluationService) {
        this.assessmentRepository = assessmentRepository;
        this.engagementRepository = engagementRepository;
        this.evidenceRepository = evidenceRepository;
        this.outreachRepository = outreachRepository;
        this.personaStateRepository = personaStateRepository;
        this.proposalRepository = proposalRepository;
        this.scenarioRepository = scenarioRepository;
        this.aiOrchestrationService = aiOrchestrationService;
        this.feedbackParser = new AssessmentFeedbackParser(objectMapper);
        this.achievementEvaluationService = achievementEvaluationService;
    }

    @Transactional
    public AssessmentResponse generate(UUID engagementId, UUID userId) {
        Engagement engagement = engagementRepository.findByIdAndUserId(engagementId, userId)
                .orElseThrow(() -> new NotFoundException("Engagement", engagementId));

        return assessmentRepository.findByEngagementId(engagementId)
                .map(AssessmentResponse::from)
                .orElseGet(() -> buildAndPersist(engagement));
    }

    @Transactional(readOnly = true)
    public AssessmentResponse get(UUID engagementId, UUID userId) {
        engagementRepository.findByIdAndUserId(engagementId, userId)
                .orElseThrow(() -> new NotFoundException("Engagement", engagementId));
        Assessment assessment = assessmentRepository.findByEngagementId(engagementId)
                .orElseThrow(() -> new NotFoundException("Assessment for engagement", engagementId));
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
        if (engagement.getState() != EngagementState.CLIENT_DECISION) {
            throw new AssessmentNotAvailableException(engagement.getState());
        }
        UUID engagementId = engagement.getId();

        int evidenceCount = evidenceRepository.findByEngagementId(engagementId).size();
        int avgOutreachScore = averageOutreachScore(outreachRepository.findByEngagementId(engagementId));
        PersonaState state = personaStateRepository.findByEngagementId(engagementId)
                .orElseGet(() -> PersonaState.initial(engagementId));
        Proposal proposal = proposalRepository.findByEngagementId(engagementId).orElse(null);
        int proposalAlignment = proposal != null ? proposal.getAlignmentScore() : 0;

        List<CompetencyScore> competencyScores = AssessmentEngine.score(
                evidenceCount, avgOutreachScore, state.getTrust(), state.getInterest(), state.getPatience(),
                proposalAlignment);
        Scenario scenario = scenarioRepository.findById(engagement.getScenarioId())
                .orElseThrow(() -> new NotFoundException("Scenario", engagement.getScenarioId()));
        int overallScore = AssessmentEngine.overall(competencyScores, scenario.getRubricWeights());
        String outcome = proposal != null && proposal.getDecision() == ProposalDecision.WON
                ? "PROPOSAL_ACCEPTED"
                : "PROPOSAL_REJECTED";

        AssessmentFeedback feedback = aiOrchestrationService.execute(
                "assessment_feedback",
                engagementId,
                buildFeedbackPrompt(competencyScores, overallScore, outcome),
                PROMPT_VERSION,
                feedbackParser,
                () -> AssessmentFeedback.safeFallback(overallScore));

        Assessment assessment = Assessment.create(engagementId, competencyScores, overallScore, outcome,
                feedback.feedbackSummary(), feedback.strengths(), feedback.improvementAreas());
        assessmentRepository.save(assessment);

        engagement.transitionTo(EngagementState.REVIEW, "Assessment generated");
        engagementRepository.save(engagement);

        achievementEvaluationService.evaluateForUser(engagement.getUserId());

        return AssessmentResponse.from(assessment);
    }

    private int averageOutreachScore(List<OutreachAttempt> attempts) {
        return (int) Math.round(attempts.stream()
                .filter(a -> a.getScorePersonalisation() != null)
                .mapToInt(a -> (a.getScorePersonalisation() + a.getScoreRelevance()
                        + a.getScoreClarity() + a.getScoreCallToAction()) / 4)
                .average()
                .orElse(0));
    }

    private String buildFeedbackPrompt(List<CompetencyScore> scores, int overallScore, String outcome) {
        StringBuilder scoreLines = new StringBuilder();
        scores.forEach(s -> scoreLines.append("- %s: %d/100 (%s)%n"
                .formatted(s.getCompetencyName(), s.getScore(), s.getEvidenceNote())));

        return """
                You are an IBM consulting coach writing evidence-based feedback for a trainee who just
                completed a training engagement with outcome: %s (overall score %d/100).

                Competency breakdown:
                %s

                Write a concise, encouraging but honest coaching summary citing these specific scores.
                Return ONLY JSON matching:
                {"feedbackSummary": string, "strengths": string[], "improvementAreas": string[]}
                """.formatted(outcome, overallScore, scoreLines);
    }

    public static class AssessmentNotAvailableException extends DomainException {
        public AssessmentNotAvailableException(EngagementState state) {
            super("Assessment is not available in state: " + state);
        }
    }
}

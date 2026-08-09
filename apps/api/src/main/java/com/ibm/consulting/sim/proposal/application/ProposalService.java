package com.ibm.consulting.sim.proposal.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ibm.consulting.sim.ai.application.AiOrchestrationService;
import com.ibm.consulting.sim.engagement.domain.Engagement;
import com.ibm.consulting.sim.engagement.domain.EngagementRepository;
import com.ibm.consulting.sim.engagement.domain.EngagementState;
import com.ibm.consulting.sim.lead.domain.ResearchEvidence;
import com.ibm.consulting.sim.lead.domain.ResearchEvidenceRepository;
import com.ibm.consulting.sim.meeting.domain.ConversationActor;
import com.ibm.consulting.sim.meeting.domain.ConversationTurnRepository;
import com.ibm.consulting.sim.meeting.domain.MeetingRepository;
import com.ibm.consulting.sim.meeting.domain.PersonaState;
import com.ibm.consulting.sim.meeting.domain.PersonaStateRepository;
import com.ibm.consulting.sim.proposal.domain.*;
import com.ibm.consulting.sim.shared.domain.DomainException;
import com.ibm.consulting.sim.shared.domain.NotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Proposal application boundary. Canonical evidence and transcript entries are
 * exposed as read-only sources; learners own the proposal draft. LLM results are
 * advisory only and never participate in the deterministic client outcome.
 */
@Service
public class ProposalService {
    private static final int PROMPT_VERSION = 1;

    private final ProposalRepository proposalRepository;
    private final EngagementRepository engagementRepository;
    private final ResearchEvidenceRepository evidenceRepository;
    private final PersonaStateRepository personaStateRepository;
    private final MeetingRepository meetingRepository;
    private final ConversationTurnRepository turnRepository;
    private final AiOrchestrationService aiOrchestrationService;
    private final ObjectMapper objectMapper;

    public ProposalService(ProposalRepository proposalRepository,
                           EngagementRepository engagementRepository,
                           ResearchEvidenceRepository evidenceRepository,
                           PersonaStateRepository personaStateRepository,
                           MeetingRepository meetingRepository,
                           ConversationTurnRepository turnRepository,
                           AiOrchestrationService aiOrchestrationService,
                           ObjectMapper objectMapper) {
        this.proposalRepository = proposalRepository;
        this.engagementRepository = engagementRepository;
        this.evidenceRepository = evidenceRepository;
        this.personaStateRepository = personaStateRepository;
        this.meetingRepository = meetingRepository;
        this.turnRepository = turnRepository;
        this.aiOrchestrationService = aiOrchestrationService;
        this.objectMapper = objectMapper;
    }

    @Transactional(readOnly = true)
    public ProposalWorkspaceResponse workspace(UUID engagementId, UUID userId) {
        Engagement engagement = loadOwnedEngagement(engagementId, userId);
        ProposalResponse proposal = proposalRepository.findByEngagementId(engagementId)
                .map(ProposalResponse::from)
                .orElse(null);
        return new ProposalWorkspaceResponse(proposal, sources(engagement));
    }

    @Transactional
    public ProposalResponse saveDraft(UUID engagementId, UUID userId, ProposalDraftContent content) {
        Engagement engagement = loadEditableEngagement(engagementId, userId);
        Proposal proposal = proposalRepository.findByEngagementId(engagementId).orElse(null);
        if (proposal == null) {
            proposal = Proposal.draft(engagementId, content);
        } else if (proposal.getStatus() == ProposalStatus.SUBMITTED) {
            throw new ProposalAlreadySubmittedException();
        } else {
            proposal.updateDraft(content);
        }
        proposalRepository.save(proposal);
        if (engagement.getState() == EngagementState.DISCOVERY_COMPLETE) {
            engagement.transitionTo(EngagementState.PROPOSAL_DRAFT, "Proposal draft created");
            engagementRepository.save(engagement);
        }
        return ProposalResponse.from(proposal);
    }

    @Transactional(readOnly = true)
    public ProposalReviewResponse review(UUID engagementId, UUID userId, ProposalDraftContent content) {
        Engagement engagement = loadOwnedEngagement(engagementId, userId);
        List<ProposalSource> sources = sources(engagement);
        List<ProposalValidationIssue> issues = ProposalValidationEngine.validate(content, sources);
        List<ClientAlignmentItem> alignment = ProposalValidationEngine.alignment(content, sources);
        ProposalReviewNarrative narrative = aiOrchestrationService.execute(
                "proposal_review", engagementId, reviewPrompt(content, alignment, issues), PROMPT_VERSION,
                new ProposalReviewParser(objectMapper), () -> ProposalReviewNarrative.fallback(issues));
        return reviewResponse(content, sources, issues, alignment, narrative);
    }

    @Transactional(readOnly = true)
    public ProposalChallengeResponse challenge(UUID engagementId, UUID userId, ProposalDraftContent content) {
        Engagement engagement = loadOwnedEngagement(engagementId, userId);
        List<ProposalSource> sources = sources(engagement);
        return aiOrchestrationService.execute(
                "proposal_challenge", engagementId, challengePrompt(content, sources), PROMPT_VERSION,
                new ProposalChallengeParser(objectMapper), () -> deterministicConcerns(content, sources));
    }

    @Transactional
    public ProposalResponse submit(UUID engagementId, UUID userId, ProposalDraftContent content,
                                   boolean enforceWorkspaceGate) {
        Engagement engagement = loadEditableEngagement(engagementId, userId);
        Proposal proposal = proposalRepository.findByEngagementId(engagementId).orElse(null);
        if (proposal != null && proposal.getStatus() == ProposalStatus.SUBMITTED) {
            throw new ProposalAlreadySubmittedException();
        }

        List<ProposalSource> sources = sources(engagement);
        List<ProposalValidationIssue> issues = ProposalValidationEngine.validate(content, sources);
        if (enforceWorkspaceGate && issues.stream().anyMatch(issue -> "BLOCKING".equals(issue.severity()))) {
            throw new ProposalValidationException(issues);
        }

        if (proposal == null) proposal = Proposal.draft(engagementId, content);
        else proposal.updateDraft(content);

        List<String> evidenceNotes = evidenceRepository.findByEngagementId(engagementId).stream()
                .map(ResearchEvidence::getNote).toList();
        PersonaState state = personaStateRepository.findByEngagementId(engagementId)
                .orElseGet(() -> PersonaState.initial(engagementId));
        List<String> outcomeComponents = new ArrayList<>(content.components());
        outcomeComponents.add(content.solutionStrategy());
        content.businessOutcomes().forEach(outcome -> outcomeComponents.add(outcome.getOutcome()));
        ProposalOutcome outcome = ProposalOutcomePolicy.evaluate(
                content.problemStatement(), outcomeComponents, evidenceNotes, state.getTrust(), state.getInterest());
        ProposalClientDecision clientDecision = aiOrchestrationService.execute(
                "proposal_client_decision", engagementId,
                clientDecisionPrompt(outcome, content, sources), PROMPT_VERSION,
                new ProposalClientDecisionParser(objectMapper), () -> ProposalClientDecision.fallback(outcome.won()));

        proposal.submit();
        proposal.resolve(outcome.alignmentScore(), outcome.won() ? ProposalDecision.WON : ProposalDecision.LOST,
                outcome.rationale(), clientDecision.message());
        proposalRepository.save(proposal);

        if (engagement.getState() == EngagementState.DISCOVERY_COMPLETE) {
            engagement.transitionTo(EngagementState.PROPOSAL_DRAFT, "Proposal draft opened during submission");
        }
        engagement.transitionTo(EngagementState.PROPOSAL_SUBMITTED, "Proposal submitted");
        engagement.transitionTo(EngagementState.CLIENT_DECISION,
                "Client decision: " + (outcome.won() ? "PROPOSAL_ACCEPTED" : "PROPOSAL_REJECTED"));
        engagementRepository.save(engagement);
        return ProposalResponse.from(proposal);
    }

    @Transactional(readOnly = true)
    public ProposalResponse get(UUID engagementId, UUID userId) {
        loadOwnedEngagement(engagementId, userId);
        Proposal proposal = proposalRepository.findByEngagementId(engagementId)
                .orElseThrow(() -> new NotFoundException("Proposal for engagement", engagementId));
        return ProposalResponse.from(proposal);
    }

    private ProposalReviewResponse reviewResponse(ProposalDraftContent content, List<ProposalSource> sources,
                                                  List<ProposalValidationIssue> issues,
                                                  List<ClientAlignmentItem> alignment,
                                                  ProposalReviewNarrative narrative) {
        return new ProposalReviewResponse(
                issues.stream().noneMatch(issue -> "BLOCKING".equals(issue.severity())),
                issues, alignment,
                ProposalValidationEngine.problemScore(content),
                ProposalValidationEngine.evidenceScore(content, sources),
                ProposalValidationEngine.alignmentScore(alignment),
                ProposalValidationEngine.commercialScore(content),
                ProposalValidationEngine.riskScore(content),
                ProposalValidationEngine.feasibilityScore(content),
                narrative.executiveFeedback(), narrative.improvementActions());
    }

    private List<ProposalSource> sources(Engagement engagement) {
        List<ProposalSource> sources = new ArrayList<>();
        evidenceRepository.findByEngagementId(engagement.getId()).forEach(evidence -> sources.add(new ProposalSource(
                "evidence:" + evidence.getId(), "E-" + String.format("%02d", evidence.getSequenceNo())
                        + " " + sourceLabel(evidence),
                "RESEARCH_EVIDENCE", evidence.getNote(), evidence.getConfidence().name())));
        meetingRepository.findByEngagementId(engagement.getId()).ifPresent(meeting ->
                turnRepository.findByMeetingIdOrderBySequenceAsc(meeting.getId()).stream()
                        .filter(turn -> turn.getActor() == ConversationActor.PERSONA)
                        .forEach(turn -> sources.add(new ProposalSource(
                                "meeting:" + turn.getId(), "M-" + turn.getSequence() + " Client discovery",
                                "MEETING_DISCOVERY", turn.getContent(), "HIGH"))));
        return List.copyOf(sources);
    }

    private String sourceLabel(ResearchEvidence evidence) {
        return evidence.getSourceTitle() == null || evidence.getSourceTitle().isBlank()
                ? evidence.getEvidenceType().name().replace('_', ' ')
                : evidence.getSourceTitle();
    }

    private ProposalChallengeResponse deterministicConcerns(ProposalDraftContent content, List<ProposalSource> sources) {
        List<ProposalValidationIssue> issues = ProposalValidationEngine.validate(content, sources);
        List<String> concerns = new ArrayList<>(issues.stream().limit(3)
                .map(issue -> "How will you address this: " + issue.message()).toList());
        if (content.businessOutcomes().isEmpty()) concerns.add("What measurable return should the client expect from the pilot?");
        if (content.risks().isEmpty()) concerns.add("How will you limit operational disruption and provide a rollback path?");
        if (concerns.isEmpty()) concerns.add("Which proposal commitment is most important for the executive sponsor to validate first?");
        return new ProposalChallengeResponse(List.copyOf(concerns));
    }

    private String reviewPrompt(ProposalDraftContent content, List<ClientAlignmentItem> alignment,
                                List<ProposalValidationIssue> issues) {
        return """
                You are an executive proposal coach. You review, never rewrite, the learner's proposal.
                The deterministic engine has already produced validation findings; do not contradict or score the proposal.
                Return ONLY JSON: {"executiveFeedback": string, "improvementActions": [string, string, string]}.
                Proposal problem: %s
                Solution: %s
                Validation: %s
                Client alignment: %s
                """.formatted(content.problemStatement(), content.solutionStrategy(), issues, alignment);
    }

    private String challengePrompt(ProposalDraftContent content, List<ProposalSource> sources) {
        String clientContext = sources.stream().limit(6).map(source -> source.label() + ": " + source.content())
                .reduce("", (left, right) -> left + "\n" + right);
        return """
                You are a client committee challenging a consulting proposal. Ask concise questions only; do not invent facts.
                Return ONLY JSON: {"concerns": [string, string, string]}.
                Proposal: %s
                Client evidence: %s
                """.formatted(content.problemStatement() + "\n" + content.solutionStrategy(), clientContext);
    }

    private String clientDecisionPrompt(ProposalOutcome outcome, ProposalDraftContent content, List<ProposalSource> sources) {
        String context = sources.stream().limit(5).map(source -> source.label() + ": " + source.content())
                .reduce("", (left, right) -> left + "\n" + right);
        return """
                You are the client sponsor responding to a consulting proposal. The backend already decided the outcome.
                Do not change, soften or challenge that outcome. Write a concise response grounded only in the supplied proposal and client evidence.
                Return ONLY JSON: {"message": string}.
                Decision: %s
                Deterministic rationale: %s
                Proposal: %s
                Client evidence: %s
                """.formatted(outcome.won() ? "PROPOSAL_ACCEPTED" : "PROPOSAL_REJECTED", outcome.rationale(),
                content.problemStatement() + "\n" + content.solutionStrategy(), context);
    }

    private Engagement loadEditableEngagement(UUID engagementId, UUID userId) {
        Engagement engagement = loadOwnedEngagement(engagementId, userId);
        if (engagement.getState() != EngagementState.DISCOVERY_COMPLETE
                && engagement.getState() != EngagementState.PROPOSAL_DRAFT) {
            throw new InvalidProposalStateException(engagement.getState());
        }
        return engagement;
    }

    private Engagement loadOwnedEngagement(UUID engagementId, UUID userId) {
        return engagementRepository.findByIdAndUserId(engagementId, userId)
                .orElseThrow(() -> new NotFoundException("Engagement", engagementId));
    }

    public static class InvalidProposalStateException extends DomainException {
        public InvalidProposalStateException(EngagementState state) {
            super("Cannot edit a proposal in state: " + state);
        }
    }

    public static class ProposalValidationException extends DomainException {
        private final List<ProposalValidationIssue> issues;
        public ProposalValidationException(List<ProposalValidationIssue> issues) {
            super("Resolve proposal validation findings before submission");
            this.issues = List.copyOf(issues);
        }
        public List<ProposalValidationIssue> getIssues() { return issues; }
    }
}

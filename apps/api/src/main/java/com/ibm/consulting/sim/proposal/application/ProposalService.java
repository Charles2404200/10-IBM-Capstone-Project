package com.ibm.consulting.sim.proposal.application;

import com.ibm.consulting.sim.engagement.domain.Engagement;
import com.ibm.consulting.sim.engagement.domain.EngagementRepository;
import com.ibm.consulting.sim.engagement.domain.EngagementState;
import com.ibm.consulting.sim.lead.domain.ResearchEvidence;
import com.ibm.consulting.sim.lead.domain.ResearchEvidenceRepository;
import com.ibm.consulting.sim.meeting.domain.PersonaState;
import com.ibm.consulting.sim.meeting.domain.PersonaStateRepository;
import com.ibm.consulting.sim.proposal.domain.*;
import com.ibm.consulting.sim.shared.domain.DomainException;
import com.ibm.consulting.sim.shared.domain.NotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * Submits proposals and resolves the deterministic contract outcome (§4.2, §8 Phase 3).
 * The AI layer is intentionally not involved here — the win/lose decision must be
 * reproducible and auditable from stored engagement data alone.
 */
@Service
public class ProposalService {

    private final ProposalRepository proposalRepository;
    private final EngagementRepository engagementRepository;
    private final ResearchEvidenceRepository evidenceRepository;
    private final PersonaStateRepository personaStateRepository;

    public ProposalService(ProposalRepository proposalRepository,
                            EngagementRepository engagementRepository,
                            ResearchEvidenceRepository evidenceRepository,
                            PersonaStateRepository personaStateRepository) {
        this.proposalRepository = proposalRepository;
        this.engagementRepository = engagementRepository;
        this.evidenceRepository = evidenceRepository;
        this.personaStateRepository = personaStateRepository;
    }

    @Transactional
    public ProposalResponse submit(UUID engagementId, UUID userId, String problemStatement,
                                    List<String> components, BigDecimal budget, int timelineWeeks) {
        Engagement engagement = engagementRepository.findByIdAndUserId(engagementId, userId)
                .orElseThrow(() -> new NotFoundException("Engagement", engagementId));

        if (engagement.getState() != EngagementState.MEETING_COMPLETED) {
            throw new InvalidProposalStateException(engagement.getState());
        }
        if (proposalRepository.findByEngagementId(engagementId).isPresent()) {
            throw new ProposalAlreadySubmittedException();
        }

        List<String> evidenceNotes = evidenceRepository.findByEngagementId(engagementId).stream()
                .map(ResearchEvidence::getNote)
                .toList();
        PersonaState state = personaStateRepository.findByEngagementId(engagementId)
                .orElseGet(() -> PersonaState.initial(engagementId));

        ProposalOutcome outcome = ProposalOutcomePolicy.evaluate(
                problemStatement, components, evidenceNotes, state.getTrust(), state.getInterest());

        Proposal proposal = Proposal.submit(engagementId, problemStatement, components, budget, timelineWeeks);
        proposal.resolve(outcome.alignmentScore(),
                outcome.won() ? ProposalDecision.WON : ProposalDecision.LOST,
                outcome.rationale());
        proposalRepository.save(proposal);

        engagement.transitionTo(EngagementState.PROPOSAL_SUBMITTED, "Proposal submitted");
        engagement.transitionTo(
                outcome.won() ? EngagementState.CONTRACT_WON : EngagementState.CONTRACT_LOST,
                "Proposal outcome: " + (outcome.won() ? "WON" : "LOST"));
        engagementRepository.save(engagement);

        return ProposalResponse.from(proposal);
    }

    @Transactional(readOnly = true)
    public ProposalResponse get(UUID engagementId, UUID userId) {
        engagementRepository.findByIdAndUserId(engagementId, userId)
                .orElseThrow(() -> new NotFoundException("Engagement", engagementId));
        Proposal proposal = proposalRepository.findByEngagementId(engagementId)
                .orElseThrow(() -> new NotFoundException("Proposal for engagement", engagementId));
        return ProposalResponse.from(proposal);
    }

    public static class InvalidProposalStateException extends DomainException {
        public InvalidProposalStateException(EngagementState state) {
            super("Cannot submit a proposal in state: " + state);
        }
    }
}

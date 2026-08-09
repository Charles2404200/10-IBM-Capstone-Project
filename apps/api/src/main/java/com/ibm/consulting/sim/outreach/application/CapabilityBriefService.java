package com.ibm.consulting.sim.outreach.application;

import com.ibm.consulting.sim.engagement.domain.Engagement;
import com.ibm.consulting.sim.engagement.domain.EngagementRepository;
import com.ibm.consulting.sim.engagement.domain.EngagementState;
import com.ibm.consulting.sim.outreach.domain.CapabilityBrief;
import com.ibm.consulting.sim.outreach.domain.CapabilityBriefRepository;
import com.ibm.consulting.sim.outreach.domain.CapabilityBriefReview;
import com.ibm.consulting.sim.outreach.domain.CapabilityBriefReviewPolicy;
import com.ibm.consulting.sim.outreach.domain.OutreachAttempt;
import com.ibm.consulting.sim.outreach.domain.OutreachNextAction;
import com.ibm.consulting.sim.outreach.domain.OutreachRepository;
import com.ibm.consulting.sim.outreach.domain.OutreachRequestPolicy;
import com.ibm.consulting.sim.shared.domain.DomainException;
import com.ibm.consulting.sim.shared.domain.NotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.UUID;

@Service
public class CapabilityBriefService {

    private final CapabilityBriefRepository briefRepository;
    private final OutreachRepository outreachRepository;
    private final EngagementRepository engagementRepository;

    public CapabilityBriefService(CapabilityBriefRepository briefRepository, OutreachRepository outreachRepository,
                                  EngagementRepository engagementRepository) {
        this.briefRepository = briefRepository;
        this.outreachRepository = outreachRepository;
        this.engagementRepository = engagementRepository;
    }

    @Transactional(readOnly = true)
    public CapabilityBriefResponse get(UUID engagementId, UUID userId) {
        requireOwnedEngagement(engagementId, userId);
        return briefRepository.findByEngagementId(engagementId).map(CapabilityBriefResponse::from).orElse(null);
    }

    @Transactional
    public CapabilityBriefResponse submit(UUID engagementId, UUID userId, String relevantExperience, String approach,
                                          String caseExample, String clientFit) {
        Engagement engagement = requireOwnedEngagement(engagementId, userId);
        OutreachAttempt latestAttempt = outreachRepository.findByEngagementId(engagementId).stream()
                .max(Comparator.comparingInt(OutreachAttempt::getAttemptNumber))
                .orElseThrow(() -> new InvalidCapabilityBriefStateException("No client request is available"));
        OutreachNextAction requiredAction = OutreachRequestPolicy.detailsFor(
                latestAttempt.getOutcome(), latestAttempt.getClientReply(), latestAttempt.getNextAction()).nextAction();
        if (requiredAction != OutreachNextAction.SUBMIT_CAPABILITY_BRIEF) {
            throw new InvalidCapabilityBriefStateException("The latest client response does not request a capability brief");
        }
        recoverPendingOutreachState(engagement);

        CapabilityBrief brief = briefRepository.findByEngagementId(engagementId)
                .orElseGet(() -> CapabilityBrief.create(engagementId, relevantExperience, approach, caseExample, clientFit));
        brief.updateContent(relevantExperience, approach, caseExample, clientFit);

        CapabilityBriefReview review = CapabilityBriefReviewPolicy.evaluate(
                relevantExperience, approach, caseExample, clientFit);
        brief.review(review.clientReply(), review.outcome(), review.clientFit(), review.industryRelevance(),
                review.evidenceQuality(), review.clarity(), review.credibility());
        CapabilityBrief saved = briefRepository.save(brief);

        if (review.outcome() == com.ibm.consulting.sim.outreach.domain.OutreachOutcome.ACCEPTED) {
            engagement.transitionTo(EngagementState.MEETING_SECURED, "Capability brief accepted by client");
            engagementRepository.save(engagement);
        }
        return CapabilityBriefResponse.from(saved);
    }

    private void recoverPendingOutreachState(Engagement engagement) {
        if (engagement.getState() == EngagementState.HYPOTHESIS_READY) {
            // Compatibility recovery for an older partial transaction: a client
            // response exists, so the engagement must be in outreach before its
            // requested artifact can be reviewed.
            engagement.transitionTo(EngagementState.OUTREACHING,
                    "Recovered pending outreach state for requested capability brief");
            engagementRepository.save(engagement);
            return;
        }
        if (engagement.getState() != EngagementState.OUTREACHING) {
            throw new InvalidCapabilityBriefStateException(
                    "A capability brief cannot be submitted in state " + engagement.getState());
        }
    }

    private Engagement requireOwnedEngagement(UUID engagementId, UUID userId) {
        return engagementRepository.findByIdAndUserId(engagementId, userId)
                .orElseThrow(() -> new NotFoundException("Engagement", engagementId));
    }

    public static class InvalidCapabilityBriefStateException extends DomainException {
        InvalidCapabilityBriefStateException(String message) { super(message); }
    }
}

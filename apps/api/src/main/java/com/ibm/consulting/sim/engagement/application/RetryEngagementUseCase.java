package com.ibm.consulting.sim.engagement.application;

import com.ibm.consulting.sim.engagement.domain.Engagement;
import com.ibm.consulting.sim.engagement.domain.EngagementRepository;
import com.ibm.consulting.sim.engagement.domain.EngagementState;
import com.ibm.consulting.sim.shared.domain.DomainException;
import com.ibm.consulting.sim.shared.domain.NotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/** Creates a fresh, traceable run from the same chosen lead after an automatic meeting failure. */
@Service
public class RetryEngagementUseCase {

    private final EngagementRepository engagementRepository;

    public RetryEngagementUseCase(EngagementRepository engagementRepository) {
        this.engagementRepository = engagementRepository;
    }

    @Transactional
    public EngagementResponse execute(UUID failedEngagementId, UUID userId) {
        Engagement failed = engagementRepository.findByIdAndUserId(failedEngagementId, userId)
                .orElseThrow(() -> new NotFoundException("Engagement", failedEngagementId));
        if (failed.getState() != EngagementState.MEETING_FAILED) {
            throw new RetryNotAvailableException("Only an engagement that failed its meeting can be restarted.");
        }
        if (failed.getSelectedLeadId() == null) {
            throw new RetryNotAvailableException("The failed engagement has no selected lead to restart.");
        }

        // A retry request can be delivered more than once if the browser loses
        // the response. Reuse the already-created active retry rather than
        // creating duplicate learner workspaces for the same failed attempt.
        var existingRetry = engagementRepository.findByUserId(userId).stream()
                .filter(candidate -> failed.getId().equals(candidate.getRetryOfEngagementId()))
                .filter(candidate -> !candidate.getState().isTerminal())
                .findFirst();
        if (existingRetry.isPresent()) {
            return EngagementResponse.from(existingRetry.get());
        }

        // Preserve the original resolved gameplay profile so a retry is assessed
        // against the same difficulty and scenario truth as the failed attempt.
        Engagement retry = Engagement.start(
                userId,
                failed.getScenarioId(),
                failed.getPersonaId(),
                failed.getDifficultyProfileSnapshot(),
                failed.getId());
        retry.selectLead(failed.getSelectedLeadId());
        engagementRepository.save(retry);
        return EngagementResponse.from(retry);
    }

    public static class RetryNotAvailableException extends DomainException {
        public RetryNotAvailableException(String message) {
            super(message);
        }
    }
}

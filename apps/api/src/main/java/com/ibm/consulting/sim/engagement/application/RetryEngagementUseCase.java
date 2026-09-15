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
    private final EngagementLifecycleCoordinator lifecycle;

    public RetryEngagementUseCase(EngagementRepository engagementRepository) {
        this(engagementRepository, EngagementLifecycleCoordinator.legacy());
    }

    @org.springframework.beans.factory.annotation.Autowired
    public RetryEngagementUseCase(EngagementRepository engagementRepository,
                                  EngagementLifecycleCoordinator lifecycle) {
        this.engagementRepository = engagementRepository;
        this.lifecycle = lifecycle;
    }

    @Transactional
    public EngagementResponse execute(UUID failedEngagementId, UUID userId) {
        Engagement failed = engagementRepository.findByIdAndUserIdForUpdate(failedEngagementId, userId)
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
            return lifecycle.response(existingRetry.get());
        }

        // Preserve the original resolved gameplay profile so a retry is assessed
        // against the same difficulty and scenario truth as the failed attempt.
        Engagement retry = Engagement.start(
                userId,
                failed.getScenarioId(),
                failed.getPersonaId(),
                failed.getDifficultyProfileSnapshot(),
                failed.getId(),
                lifecycle.retrySnapshot(failed));
        lifecycle.selectLead(retry, failed.getSelectedLeadId());
        engagementRepository.save(retry);
        return lifecycle.response(retry);
    }

    public static class RetryNotAvailableException extends DomainException {
        public RetryNotAvailableException(String message) {
            super(message);
        }
    }
}

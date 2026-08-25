package com.ibm.consulting.sim.shared.application;

import com.ibm.consulting.sim.shared.domain.OutboxEvent;
import com.ibm.consulting.sim.shared.domain.OutboxEventRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.util.UUID;

@Service
public class OutboxStateService {

    private static final Duration RETRY_DELAY = Duration.ofSeconds(1);

    private final OutboxEventRepository outboxRepository;

    public OutboxStateService(OutboxEventRepository repo)
    {
        this.outboxRepository = repo;

    }
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markPublished(UUID eventId) {
        OutboxEvent event = findForUpdate(eventId);
        event.markPublished();
        outboxRepository.save(event);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markPendingAgain(UUID eventId) {
        OutboxEvent event = findForUpdate(eventId);
        event.markRetry(RETRY_DELAY);
        outboxRepository.save(event);
    }

    private OutboxEvent findForUpdate(UUID eventId) {
        return outboxRepository.findByIdForUpdate(eventId)
                .orElseThrow(() -> new IllegalStateException(
                        "Outbox event not found: " + eventId
                ));
    }
}

package com.ibm.consulting.sim.shared.application;

import com.ibm.consulting.sim.shared.domain.OutboxEvent;
import com.ibm.consulting.sim.shared.infrastructure.JPAOutboxRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class OutboxStateService {

    private final JPAOutboxRepository outboxRepository;

    public OutboxStateService(JPAOutboxRepository repo)
    {
        this.outboxRepository = repo;

    }
    // requires new means telling explicitly that it required new transaction
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean tryMarkProcessing(UUID eventId) {

        int updated =
                outboxRepository.markProcessingIfPending(eventId);

        return updated == 1;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markPublished(UUID eventId) {

        outboxRepository.markPublished(eventId);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markPendingAgain(UUID eventId) {

        outboxRepository.markPendingAgain(eventId);
    }
}
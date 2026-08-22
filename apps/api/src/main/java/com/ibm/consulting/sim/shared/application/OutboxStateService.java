package com.ibm.consulting.sim.shared.application;

import com.ibm.consulting.sim.shared.domain.OutboxEvent;
import com.ibm.consulting.sim.shared.infrastructure.JPAOutboxRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class OutboxStateService {

    private final JPAOutboxRepository outboxRepository;

    public OutboxStateService(JPAOutboxRepository repo)
    {
        this.outboxRepository = repo;

    }

    @Transactional
    public void markProcessing(UUID eventId) {

        OutboxEvent event =
                outboxRepository.findById(eventId)
                        .orElseThrow();

        event.markProcessing();
    }

    @Transactional
    public void markPublished(UUID eventId) {

        OutboxEvent event =
                outboxRepository.findById(eventId)
                        .orElseThrow();

        event.markPublished();
    }

    @Transactional
    public void markPendingAgain(UUID eventId) {

        OutboxEvent event =
                outboxRepository.findById(eventId)
                        .orElseThrow();

        event.retry();
    }
}
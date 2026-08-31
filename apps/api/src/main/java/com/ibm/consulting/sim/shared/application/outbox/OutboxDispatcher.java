package com.ibm.consulting.sim.shared.application.outbox;

import com.ibm.consulting.sim.shared.application.kafka.KafkaEventPublisher;
import com.ibm.consulting.sim.shared.domain.outbox.OutboxEvent;
import com.ibm.consulting.sim.shared.domain.outbox.OutboxEventRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;

@Component
public class OutboxDispatcher {

    private static final Logger log = LoggerFactory.getLogger(OutboxDispatcher.class);

    private final OutboxClaimService claimService;
    private final OutboxEventRepository repository;
    private final OutboxStateService stateService;
    private final KafkaEventPublisher publisher;
    private final ExecutorService completionExecutor;
    private final int batchSize;

    public OutboxDispatcher(
            OutboxClaimService claimService,
            OutboxEventRepository repository,
            OutboxStateService stateService,
            KafkaEventPublisher publisher,
            @Qualifier("outboxCompletionExecutor") ExecutorService completionExecutor,
            @Value("${app.kafka.outbox.batch-size:100}") int batchSize) {
        if (batchSize <= 0) {
            throw new IllegalArgumentException("Outbox batch size must be positive");
        }
        this.claimService = claimService;
        this.repository = repository;
        this.stateService = stateService;
        this.publisher = publisher;
        this.completionExecutor = completionExecutor;
        this.batchSize = batchSize;
    }


    @Scheduled(fixedDelayString = "${app.kafka.outbox.poll-delay-ms:200}")
    public void dispatch() {

        UUID claimToken = UUID.randomUUID();
        List<UUID> ids =
                claimService.claimBatch(batchSize, claimToken);

        List<CompletableFuture<Void>> completions = ids.stream()
                .map(id -> publishClaimed(id, claimToken))
                .toList();

        CompletableFuture.allOf(
                completions.toArray(CompletableFuture[]::new)
        ).join();
    }

    private CompletableFuture<Void> publishClaimed(UUID id, UUID claimToken) {
        OutboxEvent event = repository.findById(id).orElse(null);
        if (event == null) {
            log.error("Claimed outbox event disappeared: eventId={}", id);
            return CompletableFuture.completedFuture(null);
        }

        try {
            return publisher.publish(event.getTopic(), event.toEnvelope())
                    .handleAsync((ignored, failure) -> {
                        completePublication(event, claimToken, failure);
                        return null;
                    }, completionExecutor);
        } catch (Exception failure) {
            completePublication(event, claimToken, failure);
            return CompletableFuture.completedFuture(null);
        }
    }

    private void completePublication(
            OutboxEvent event,
            UUID claimToken,
            Throwable failure) {
        try {
            if (failure == null) {
                stateService.markPublished(
                        event.getId(),
                        claimToken
                );
            } else {
                stateService.markPendingAgain(
                        event.getId(),
                        claimToken,
                        event.getAttemptCount()
                );
                log.warn(
                        "Outbox publication failed: eventId={}, eventType={}, topic={}",
                        event.getId(),
                        event.getEventType(),
                        event.getTopic(),
                        failure
                );
            }
        } catch (Exception stateFailure) {
            // Leave PROCESSING ownership intact. The lease-recovery scheduler
            // will safely reclaim it if this database transition cannot commit.
            log.error(
                    "Outbox completion state update failed: eventId={}, claimToken={}",
                    event.getId(),
                    claimToken,
                    stateFailure
            );
        }
    }
}

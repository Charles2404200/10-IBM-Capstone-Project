package com.ibm.consulting.sim.shared.application.outbox;

import com.ibm.consulting.sim.shared.application.kafka.KafkaEventPublisher;
import com.ibm.consulting.sim.shared.domain.outbox.OutboxEvent;
import com.ibm.consulting.sim.shared.domain.outbox.OutboxEventRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import com.ibm.consulting.sim.shared.config.OutboxProperties;
import jakarta.annotation.PreDestroy;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicBoolean;

@Component
public class OutboxDispatcher {

    private static final Logger log = LoggerFactory.getLogger(OutboxDispatcher.class);

    private final OutboxClaimService claimService;
    private final OutboxEventRepository repository;
    private final OutboxStateService stateService;
    private final KafkaEventPublisher publisher;
    private final OutboxMetrics metrics;
    private final ExecutorService completionExecutor;
    private final int batchSize;
    private final AtomicBoolean acceptingWork = new AtomicBoolean(true);

    public OutboxDispatcher(
            OutboxClaimService claimService,
            OutboxEventRepository repository,
            OutboxStateService stateService,
            KafkaEventPublisher publisher,
            OutboxMetrics metrics,
            @Qualifier("outboxCompletionExecutor") ExecutorService completionExecutor,
            OutboxProperties properties) {
        this.claimService = claimService;
        this.repository = repository;
        this.stateService = stateService;
        this.publisher = publisher;
        this.metrics = metrics;
        this.completionExecutor = completionExecutor;
        this.batchSize = properties.batchSize();
    }


    @Scheduled(fixedDelayString = "#{@outboxScheduleIntervals.pollDelayMillis()}")
    public void dispatch() {

        // Scheduled invocations racing with context shutdown must not acquire
        // new leases. Already claimed work is allowed to finish below.
        if (!acceptingWork.get()) {
            return;
        }

        // One unguessable token owns this claimed batch. Every later state
        // transition also checks the token, preventing a recovered stale worker
        // from overwriting the state written by the current owner.
        UUID claimToken = UUID.randomUUID();
        List<UUID> ids =
                claimService.claimBatch(batchSize, claimToken);

        metrics.recordClaimed(ids.size());
        if (!ids.isEmpty()) {
            log.debug(
                    "Claimed outbox batch: count={}, claimToken={}",
                    ids.size(),
                    claimToken
            );
        }

        List<CompletableFuture<Void>> completions = ids.stream()
                .map(id -> publishClaimed(id, claimToken))
                .toList();

        CompletableFuture.allOf(
                completions.toArray(CompletableFuture[]::new)
        ).join();
    }

    @PreDestroy
    public void stopClaiming() {
        acceptingWork.set(false);
    }

    private CompletableFuture<Void> publishClaimed(UUID id, UUID claimToken) {
        OutboxEvent event;
        try {
            event = repository.findById(id).orElse(null);
        } catch (Exception loadFailure) {
            // Isolate one failed row from the rest of the claimed batch. Its
            // lease remains intact and stale recovery will make it eligible
            // again if this instance cannot load it before the lease expires.
            metrics.recordClaimedEventLoadFailure();
            log.error(
                    "Unable to load claimed outbox event: eventId={}, claimToken={}",
                    id,
                    claimToken,
                    loadFailure
            );
            return CompletableFuture.completedFuture(null);
        }
        if (event == null) {
            metrics.recordClaimedEventLoadFailure();
            log.error("Claimed outbox event disappeared: eventId={}", id);
            return CompletableFuture.completedFuture(null);
        }

        try {
            // Publication is asynchronous, but the database transition happens
            // only after Kafka completes the send. Marking PUBLISHED before this
            // callback would allow message loss on a broker failure.
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
                boolean completed = stateService.markPublished(
                        event.getId(),
                        claimToken
                );
                if (completed) {
                    metrics.recordSuccess(event.getEventPriority());
                    log.debug(
                            "Published outbox event: eventId={}, eventType={}, topic={}, priority={}",
                            event.getId(),
                            event.getEventType(),
                            event.getTopic(),
                            event.getEventPriority()
                    );
                } else {
                    // Zero updated rows is not retried here: this worker no
                    // longer owns the lease and must not modify the new owner.
                    metrics.recordOwnershipConflict();
                }
            } else {
                metrics.recordFailure(event.getEventPriority());
                OutboxFailureOutcome outcome = stateService.recordFailure(
                        event.getId(),
                        claimToken,
                        event.getAttemptCount(),
                        failure
                );
                switch (outcome) {
                    case RETRY_SCHEDULED -> metrics.recordRetry(event.getEventPriority());
                    case TERMINALLY_FAILED -> metrics.recordTerminalFailure(event.getEventPriority());
                    case OWNERSHIP_LOST -> {
                        // Recovery may have reassigned the event while Kafka was
                        // failing; the current worker must treat that lease as lost.
                        metrics.recordOwnershipConflict();
                    }
                }
                log.warn(
                        "Outbox publication failed: eventId={}, eventType={}, topic={}, "
                                + "priority={}, attempt={}, outcome={}",
                        event.getId(),
                        event.getEventType(),
                        event.getTopic(),
                        event.getEventPriority(),
                        event.getAttemptCount() + 1,
                        outcome,
                        failure
                );
            }
        } catch (Exception stateFailure) {
            // Leave PROCESSING ownership intact. The lease-recovery scheduler
            // will safely reclaim it if this database transition cannot commit.
            metrics.recordCompletionStateFailure();
            log.error(
                    "Outbox completion state update failed: eventId={}, claimToken={}",
                    event.getId(),
                    claimToken,
                    stateFailure
            );
        }
    }
}

package com.ibm.consulting.sim.shared.application;

import com.ibm.consulting.sim.shared.application.kafka.KafkaEventPublisher;
import com.ibm.consulting.sim.shared.application.outbox.OutboxClaimService;
import com.ibm.consulting.sim.shared.application.outbox.OutboxDispatcher;
import com.ibm.consulting.sim.shared.application.outbox.OutboxMetrics;
import com.ibm.consulting.sim.shared.application.outbox.OutboxStateService;
import com.ibm.consulting.sim.shared.domain.outbox.OutboxEvent;
import com.ibm.consulting.sim.shared.domain.outbox.OutboxEventRepository;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.junit.jupiter.api.Assertions.assertThrows;

class OutboxDispatcherTest {

    @Test
    void rejectsNonPositiveDispatchBatchSize() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new OutboxDispatcher(
                        mock(OutboxClaimService.class),
                        mock(OutboxEventRepository.class),
                        mock(OutboxStateService.class),
                        mock(KafkaEventPublisher.class),
                        mock(OutboxMetrics.class),
                        mock(ExecutorService.class),
                        0
                )
        );
    }

    @Test
    void startsEveryBrokerSendBeforeWaitingForAcknowledgements() throws Exception {
        OutboxClaimService claimService = mock(OutboxClaimService.class);
        OutboxEventRepository repository = mock(OutboxEventRepository.class);
        OutboxStateService stateService = mock(OutboxStateService.class);
        KafkaEventPublisher publisher = mock(KafkaEventPublisher.class);
        OutboxMetrics metrics = mock(OutboxMetrics.class);
        ExecutorService completionExecutor = Executors.newFixedThreadPool(2);

        UUID firstId = UUID.randomUUID();
        UUID secondId = UUID.randomUUID();
        OutboxEvent first = OutboxEvent.unordered(firstId, "notifications", "FIRST", 1, "{}");
        OutboxEvent second = OutboxEvent.unordered(secondId, "notifications", "SECOND", 1, "{}");
        CompletableFuture<Object> firstAck = new CompletableFuture<>();
        CompletableFuture<Object> secondAck = new CompletableFuture<>();

        when(claimService.claimBatch(eq(10), any())).thenReturn(List.of(firstId, secondId));
        when(repository.findById(firstId)).thenReturn(Optional.of(first));
        when(repository.findById(secondId)).thenReturn(Optional.of(second));
        when(stateService.markPublished(any(), any())).thenReturn(true);
        doReturn(firstAck, secondAck)
                .when(publisher).publish(eq("notifications"), any());

        OutboxDispatcher dispatcher = new OutboxDispatcher(
                claimService, repository, stateService, publisher, metrics, completionExecutor, 10);

        try {
            CompletableFuture<Void> dispatch = CompletableFuture.runAsync(dispatcher::dispatch);

            verify(publisher, timeout(2_000).times(2)).publish(eq("notifications"), any());
            firstAck.complete(null);
            secondAck.complete(null);
            dispatch.get(2, TimeUnit.SECONDS);

            verify(stateService).markPublished(eq(firstId), any());
            verify(stateService).markPublished(eq(secondId), any());
            verify(metrics, org.mockito.Mockito.times(2))
                    .recordSuccess(first.getEventPriority());
            verify(metrics).recordClaimed(2);
        } finally {
            completionExecutor.shutdownNow();
        }
    }

    @Test
    void kafkaFailureSchedulesRetryWithoutPublishingState() {
        OutboxClaimService claimService = mock(OutboxClaimService.class);
        OutboxEventRepository repository = mock(OutboxEventRepository.class);
        OutboxStateService stateService = mock(OutboxStateService.class);
        KafkaEventPublisher publisher = mock(KafkaEventPublisher.class);
        OutboxMetrics metrics = mock(OutboxMetrics.class);
        ExecutorService completionExecutor = Executors.newSingleThreadExecutor();
        UUID eventId = UUID.randomUUID();
        OutboxEvent event = OutboxEvent.unordered(
                eventId, "notifications", "TEST", 1, "{}"
        );

        when(claimService.claimBatch(eq(10), any())).thenReturn(List.of(eventId));
        when(repository.findById(eventId)).thenReturn(Optional.of(event));
        when(publisher.publish(eq("notifications"), any()))
                .thenReturn(CompletableFuture.failedFuture(
                        new IllegalStateException("broker unavailable")));
        when(stateService.markPendingAgain(eq(eventId), any(), eq(0))).thenReturn(true);

        try {
            new OutboxDispatcher(
                    claimService, repository, stateService, publisher,
                    metrics, completionExecutor, 10
            ).dispatch();

            verify(stateService).markPendingAgain(eq(eventId), any(), eq(0));
            verify(metrics).recordFailure(event.getEventPriority());
            verify(metrics).recordRetry(event.getEventPriority());
            verify(stateService, org.mockito.Mockito.never()).markPublished(any(), any());
        } finally {
            completionExecutor.shutdownNow();
        }
    }

    @Test
    void staleCompletionIsRecordedAsOwnershipConflict() {
        OutboxClaimService claimService = mock(OutboxClaimService.class);
        OutboxEventRepository repository = mock(OutboxEventRepository.class);
        OutboxStateService stateService = mock(OutboxStateService.class);
        KafkaEventPublisher publisher = mock(KafkaEventPublisher.class);
        OutboxMetrics metrics = mock(OutboxMetrics.class);
        ExecutorService completionExecutor = Executors.newSingleThreadExecutor();
        UUID eventId = UUID.randomUUID();
        OutboxEvent event = OutboxEvent.unordered(
                eventId, "notifications", "TEST", 1, "{}"
        );

        when(claimService.claimBatch(eq(10), any())).thenReturn(List.of(eventId));
        when(repository.findById(eventId)).thenReturn(Optional.of(event));
        when(publisher.publish(eq("notifications"), any()))
                .thenReturn(CompletableFuture.completedFuture(null));
        when(stateService.markPublished(eq(eventId), any())).thenReturn(false);

        try {
            new OutboxDispatcher(
                    claimService, repository, stateService, publisher,
                    metrics, completionExecutor, 10
            ).dispatch();

            verify(metrics).recordOwnershipConflict();
            verify(metrics, org.mockito.Mockito.never())
                    .recordSuccess(event.getEventPriority());
        } finally {
            completionExecutor.shutdownNow();
        }
    }

    @Test
    void oneClaimedRowLoadFailureDoesNotAbortRemainingBatch() {
        OutboxClaimService claimService = mock(OutboxClaimService.class);
        OutboxEventRepository repository = mock(OutboxEventRepository.class);
        OutboxStateService stateService = mock(OutboxStateService.class);
        KafkaEventPublisher publisher = mock(KafkaEventPublisher.class);
        OutboxMetrics metrics = mock(OutboxMetrics.class);
        ExecutorService completionExecutor = Executors.newSingleThreadExecutor();
        UUID failedId = UUID.randomUUID();
        UUID healthyId = UUID.randomUUID();
        OutboxEvent healthy = OutboxEvent.unordered(
                healthyId, "notifications", "HEALTHY", 1, "{}"
        );

        when(claimService.claimBatch(eq(10), any()))
                .thenReturn(List.of(failedId, healthyId));
        when(repository.findById(failedId))
                .thenThrow(new IllegalStateException("database read failed"));
        when(repository.findById(healthyId)).thenReturn(Optional.of(healthy));
        when(publisher.publish(eq("notifications"), any()))
                .thenReturn(CompletableFuture.completedFuture(null));
        when(stateService.markPublished(eq(healthyId), any())).thenReturn(true);

        try {
            new OutboxDispatcher(
                    claimService, repository, stateService, publisher,
                    metrics, completionExecutor, 10
            ).dispatch();

            verify(metrics).recordClaimedEventLoadFailure();
            verify(publisher).publish(eq("notifications"), any());
            verify(stateService).markPublished(eq(healthyId), any());
        } finally {
            completionExecutor.shutdownNow();
        }
    }
}

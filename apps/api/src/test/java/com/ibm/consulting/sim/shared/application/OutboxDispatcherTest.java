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

class OutboxDispatcherTest {

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
        } finally {
            completionExecutor.shutdownNow();
        }
    }
}

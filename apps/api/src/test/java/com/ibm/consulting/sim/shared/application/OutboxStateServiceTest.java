package com.ibm.consulting.sim.shared.application;

import com.ibm.consulting.sim.shared.application.outbox.OutboxFailureOutcome;
import com.ibm.consulting.sim.shared.application.outbox.OutboxRetryPolicy;
import com.ibm.consulting.sim.shared.application.outbox.OutboxStateService;
import com.ibm.consulting.sim.shared.domain.outbox.OutboxEventRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Duration;
import java.util.UUID;

import static com.ibm.consulting.sim.shared.config.TestKafkaProperties.outbox;
import static com.ibm.consulting.sim.shared.config.TestKafkaProperties.retry;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OutboxStateServiceTest {

    @Test
    void schedulesRetryBeforeConfiguredMaximum() {
        OutboxEventRepository repository = mock(OutboxEventRepository.class);
        OutboxRetryPolicy retryPolicy = mock(OutboxRetryPolicy.class);
        when(retryPolicy.delayFor(any(), eq(1))).thenReturn(Duration.ofSeconds(5));
        when(repository.markPendingAgainIfOwned(any(), any(), any())).thenReturn(1);
        OutboxStateService service = new OutboxStateService(
                repository, retryPolicy,
                outbox(100, 3, Duration.ofDays(2), 1_000, retry()));

        OutboxFailureOutcome outcome = service.recordFailure(
                UUID.randomUUID(), UUID.randomUUID(), 1,
                new IllegalStateException("temporary"));

        assertEquals(OutboxFailureOutcome.RETRY_SCHEDULED, outcome);
        verify(repository).markPendingAgainIfOwned(any(), any(), any());
        // never() means that the method is never called
        verify(repository, never()).markFailedIfOwned(any(), any(), any());
    }

    @Test
    void movesOwnedEventToFailedOnConfiguredMaximumAttempt() {
        OutboxEventRepository repository = mock(OutboxEventRepository.class);
        when(repository.markFailedIfOwned(any(), any(), any())).thenReturn(1);
        OutboxStateService service = new OutboxStateService(
                repository, mock(OutboxRetryPolicy.class),
                outbox(100, 3, Duration.ofDays(2), 1_000, retry()));
        UUID eventId = UUID.randomUUID();
        UUID claimToken = UUID.randomUUID();

        OutboxFailureOutcome outcome = service.recordFailure(
                eventId, claimToken, 2,
                new IllegalArgumentException("invalid\nrequest"));

        assertEquals(OutboxFailureOutcome.TERMINALLY_FAILED, outcome);
        ArgumentCaptor<String> description = ArgumentCaptor.forClass(String.class);
        verify(repository).markFailedIfOwned(eq(eventId), eq(claimToken), description.capture());
        assertFalse(description.getValue().contains("\n"));
        // never() is used to make sure that this method is never called
        verify(repository, never()).markPendingAgainIfOwned(any(), any(), any());
    }
}

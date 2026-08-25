package com.ibm.consulting.sim.shared.application;

import com.ibm.consulting.sim.shared.domain.OutboxEvent;
import com.ibm.consulting.sim.shared.domain.OutboxEventRepository;
import com.ibm.consulting.sim.shared.domain.OutboxStatus;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OutboxClaimServiceTest {

    private final OutboxEventRepository repository =
            mock(OutboxEventRepository.class);
    private final OutboxClaimService service =
            new OutboxClaimService(repository);

    @Test
    void marksLockedEventsProcessingAndReturnsTheirIds() {
        UUID eventId = UUID.randomUUID();
        OutboxEvent event = OutboxEvent.unordered(
                eventId,
                "notifications",
                "NOTIFICATION_PUBLISHED",
                1,
                "{}"
        );
        when(repository.findDispatchableForUpdate(25))
                .thenReturn(List.of(event));

        List<UUID> claimedIds = service.claimBatch(25);

        assertEquals(List.of(eventId), claimedIds);
        assertEquals(OutboxStatus.PROCESSING, event.getStatus());
        assertNotNull(event.getProcessingStartedAt());
        verify(repository).findDispatchableForUpdate(25);
    }
}

package com.ibm.consulting.sim.shared.application;

import com.ibm.consulting.sim.shared.domain.OutboxEvent;
import com.ibm.consulting.sim.shared.domain.OutboxEventRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class OutboxClaimService {

    private final OutboxEventRepository repository;

    public OutboxClaimService(OutboxEventRepository repository)
    {
        this.repository = repository;
    }

    @Transactional(
            propagation =
                    Propagation.REQUIRES_NEW
    )
    public List<UUID> claimBatch(
            int limit
    ) {

        List<OutboxEvent> events =
                repository.findDispatchableForUpdate(
                        limit
                );

        for (OutboxEvent event : events) {
            event.markProcessing();
        }

        /*
         * Transaction commits here.
         *
         * Other application instances now see
         * PROCESSING and cannot claim them.
         */
        return events.stream()
                .map(OutboxEvent::getId)
                .toList();
    }
}
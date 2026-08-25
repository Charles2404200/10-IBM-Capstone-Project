package com.ibm.consulting.sim.shared.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ibm.consulting.sim.shared.domain.EventSequenceRepository;
import com.ibm.consulting.sim.shared.domain.OutboxEvent;
import com.ibm.consulting.sim.shared.domain.OutboxEventRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class OutboxService {

    private final OutboxEventRepository repository;

    private final EventSequenceRepository sequenceRepository;

    private final ObjectMapper objectMapper;

    public OutboxService(
            OutboxEventRepository outboxEventRepository,
            EventSequenceRepository eventSequenceRepository,
            ObjectMapper objectMapper
    )
    {
        this.repository = outboxEventRepository;
        this.sequenceRepository = eventSequenceRepository;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public UUID enqueueUnordered(
            String topic,
            String eventType,
            int schemaVersion,
            Object payload
    ) {

        UUID eventId =
                UUID.randomUUID();

        String json =
                serialize(payload);

        OutboxEvent outbox =
                OutboxEvent.unordered(
                        eventId,
                        topic,
                        eventType,
                        schemaVersion,
                        json
                );

        repository.save(outbox);

        return eventId;
    }

    @Transactional
    public UUID enqueueOrdered(
            String topic,
            String eventType,
            int schemaVersion,
            String orderingKey,
            Object payload
    ) {

        UUID eventId =
                UUID.randomUUID();

        long sequence =
                sequenceRepository.next(
                        orderingKey
                );

        String json =
                serialize(payload);

        OutboxEvent outbox =
                OutboxEvent.ordered(
                        eventId,
                        topic,
                        eventType,
                        schemaVersion,
                        orderingKey,
                        sequence,
                        json
                );

        repository.save(outbox);

        return eventId;
    }

    private String serialize(
            Object payload
    ) {

        try {
            return objectMapper.writeValueAsString(
                    payload
            );

        } catch (JsonProcessingException e) {

            throw new IllegalArgumentException(
                    "Cannot serialize outbox payload",
                    e
            );
        }
    }
}
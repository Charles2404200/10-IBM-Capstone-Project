package com.ibm.consulting.sim.shared.domain.kafka;

import org.springframework.data.repository.query.Param;

import java.util.UUID;

public interface KafkaInboxRepository {

    int insertIfAbsent(

            String consumerGroup,

            UUID eventId,

            String eventType,

            String topic,

            int partition,

            long offset
    );
}

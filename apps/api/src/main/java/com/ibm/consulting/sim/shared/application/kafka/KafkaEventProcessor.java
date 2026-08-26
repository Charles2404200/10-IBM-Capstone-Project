package com.ibm.consulting.sim.shared.application.kafka;

import com.ibm.consulting.sim.shared.domain.outbox.EventEnvelope;
import com.ibm.consulting.sim.shared.domain.outbox.OrderingMode;
import com.ibm.consulting.sim.shared.domain.kafka.InvalidKafkaEventException;
import com.ibm.consulting.sim.shared.domain.kafka.KafkaInboxRepository;

import org.apache.kafka.clients.consumer.ConsumerRecord;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class KafkaEventProcessor {

    private static final Logger log =
            LoggerFactory.getLogger(
                    KafkaEventProcessor.class
            );

    private final KafkaInboxRepository inboxRepository;

    private final KafkaEventHandlerRegistry handlerRegistry;

    public KafkaEventProcessor(
            KafkaInboxRepository inboxRepository,
            KafkaEventHandlerRegistry handlerRegistry
    ) {

        this.inboxRepository = inboxRepository;
        this.handlerRegistry = handlerRegistry;
    }

    @Transactional
    public void process(
            String consumerGroup,
            ConsumerRecord<String, EventEnvelope> record
    ) {

        EventEnvelope event =
                record.value();

        validate(
                record,
                event
        );

        /*
         * Atomic idempotency claim.
         */
        int inserted =
                inboxRepository.insertIfAbsent(
                        consumerGroup,
                        event.eventId(),
                        event.eventType(),
                        record.topic(),
                        record.partition(),
                        record.offset()
                );

        if (inserted == 0) {

            log.debug(
                    "Duplicate Kafka event ignored: eventId={}, eventType={}, topic={}, partition={}, offset={}",
                    event.eventId(),
                    event.eventType(),
                    record.topic(),
                    record.partition(),
                    record.offset()
            );

            return;
        }

        KafkaEventContext context =
                new KafkaEventContext(
                        event.eventId(),
                        record.topic(),
                        record.partition(),
                        record.offset(),
                        record.key(),
                        event.eventType(),
                        event.schemaVersion(),
                        event.orderingMode(),
                        event.orderingKey(),
                        event.sequenceNumber(),
                        event.occurredAt()
                );

        /*
         * If this throws:
         *
         * business DB transaction rolls back
         * +
         * kafka_inbox insert rolls back.
         *
         * Kafka retry can therefore safely process again.
         */
        handlerRegistry.dispatch(
                event,
                context
        );
    }

    private void validate(
            ConsumerRecord<String, EventEnvelope> record,
            EventEnvelope event
    ) {

        if (event == null) {
            throw new InvalidKafkaEventException(
                    "Kafka event cannot be null"
            );
        }

        if (event.eventId() == null) {
            throw new InvalidKafkaEventException(
                    "eventId required"
            );
        }

        if (event.eventType() == null ||
                event.eventType().isBlank()) {

            throw new InvalidKafkaEventException(
                    "eventType required"
            );
        }

        if (event.orderingMode()
                == OrderingMode.ORDERED) {

            if (event.orderingKey() == null ||
                    event.orderingKey().isBlank()) {

                throw new InvalidKafkaEventException(
                        "ORDERED event requires orderingKey"
                );
            }

            /*
             * Critical invariant:
             *
             * Kafka key must be the ordering key.
             */
            if (!event.orderingKey()
                    .equals(record.key())) {

                throw new InvalidKafkaEventException(
                        "Kafka key does not match orderingKey"
                );
            }

            if (event.sequenceNumber() == null ||
                    event.sequenceNumber() <= 0) {

                throw new InvalidKafkaEventException(
                        "ORDERED event requires sequence"
                );
            }
        }
    }
}
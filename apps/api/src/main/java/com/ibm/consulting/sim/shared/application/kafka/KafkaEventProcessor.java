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
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.Objects;

@Service
public class KafkaEventProcessor {

    private static final Logger log =
            LoggerFactory.getLogger(
                    KafkaEventProcessor.class
            );

    private final KafkaInboxRepository inboxRepository;

    private final KafkaEventHandlerRegistry handlerRegistry;
    private final KafkaInboxMetrics metrics;

    public KafkaEventProcessor(
            KafkaInboxRepository inboxRepository,
            KafkaEventHandlerRegistry handlerRegistry,
            KafkaInboxMetrics metrics
    ) {

        this.inboxRepository = inboxRepository;
        this.handlerRegistry = handlerRegistry;
        this.metrics = metrics;
    }

    // The inbox claim and business handler share this transaction. A handler
    // failure therefore removes the claim on rollback, allowing Kafka retry to
    // execute the business operation again instead of losing the event.
    @Transactional
    public void process(
            String consumerGroup,
            ConsumerRecord<String, EventEnvelope> record
    ) {

        // The consumer group is part of the durable deduplication key; accepting
        // an empty value would collapse independent consumers into one namespace.
        if (consumerGroup == null || consumerGroup.isBlank()) {
            throw new InvalidKafkaEventException(
                    "consumerGroup required"
            );
        }
        Objects.requireNonNull(record, "record must not be null");

        EventEnvelope event =
                record.value();

        validate(
                record,
                event
        );

        /*
         * Claim this event by inserting its (consumerGroup, eventId) into the
         * inbox. The database performs the insert atomically, so concurrent
         * deliveries cannot both claim and dispatch the same event.
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

            // This consumer group committed the event previously; acknowledge
            // this delivery without invoking the business handler again.
            metrics.recordDuplicate();
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
                        event.priority(),
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

        // The metric represents committed business processing, not merely a
        // handler invocation that may still roll back with the inbox insert.
        TransactionSynchronizationManager.registerSynchronization(
                new TransactionSynchronization() {
                    @Override
                    public void afterCommit() {
                        metrics.recordProcessed();
                    }
                }
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

            // Ordered events must declare the logical stream that Kafka uses
            // to route related records to the same partition.
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

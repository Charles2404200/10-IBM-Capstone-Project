package com.ibm.consulting.sim.shared.application;

import com.ibm.consulting.sim.shared.domain.EventEnvelope;
import com.ibm.consulting.sim.shared.domain.OrderingMode;
import com.ibm.consulting.sim.shared.domain.OutboxEvent;
import com.ibm.consulting.sim.shared.infrastructure.JPAOutboxRepository;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

@Component
public class OutboxDispatcher {

    // the outbox pattern exists so that
    // we do not need one atomic
    // operation where
    // i want both the kafka transaction
    // and the database transaction to be
    // successful together
    // i should put every event
    // which is ordered in the database
    // and update whether it is processed
    // so i know whether the event is processed
    // or not and published and use it to see
    // whether the order is proper or not
    // and then if nothing is there before the
    // sequence it means all of them are published
    // and so i can remove all the published ones
    // every day

    private final JPAOutboxRepository outboxRepository;
    private final KafkaTemplate<String, EventEnvelope> kafkaTemplate;
    private final SimpMessagingTemplate messagingTemplate;
    private final OutboxStateService outboxStateService;

    public OutboxDispatcher(
            JPAOutboxRepository outboxRepository,
            KafkaTemplate<String, EventEnvelope> kafkaTemplate,
            SimpMessagingTemplate messagingTemplate,
            OutboxStateService outboxStateService
    ) {
        this.outboxRepository = outboxRepository;
        this.kafkaTemplate = kafkaTemplate;
        this.messagingTemplate = messagingTemplate;
        this.outboxStateService = outboxStateService;
    }

    // every 10 minutes 0 seconds
    @Scheduled(cron = "0 */10 * * * *")
    @Transactional
    public void cleanupPublishedEvents() {

        Instant cutoff = Instant.now().minus(2, ChronoUnit.DAYS);

        outboxRepository.deletePublishedBefore(cutoff);
    }

    @Scheduled(fixedDelay = 200)
    public void process() {

        List<OutboxEvent> events =
                outboxRepository.findDispatchableEvents(100);

        for (OutboxEvent event : events) {
            try
            {
                if (event.getOrderingMode() == OrderingMode.ORDERED) {
                    processOrdered(event);
                } else {
                    processUnordered(event);
                }
            }
            catch(Exception e)
            {
                // so kafka cannot process the event
                break;
            }
        }
    }

    private void processOrdered(OutboxEvent event) {

        EventEnvelope envelope =
                new EventEnvelope(
                        event.getId(),
                        event.getEventType(),
                        event.getOrderingKey(),
                        event.getSequenceNumber(),
                        event.getDest(),
                        event.getPayload()
                );

        try {

            boolean processed = outboxStateService.tryMarkProcessing(event.getId());

            if(processed)
            {
                return;
            }

            kafkaTemplate.send(
                    event.getTopic(),

                    // VERY IMPORTANT
                    event.getOrderingKey(),

                    envelope
            ).join();

            outboxStateService.markPublished(event.getId());

        } catch (Exception e) {

            outboxStateService.markPendingAgain(event.getId());

            // Stop hammering Kafka
            throw e;
        }
    }

    // i do not want database
    // transaction to be dependent on
    // the Web Socket event
    private void processUnordered(OutboxEvent event) {

        try {

            // this makes sure that other threads or servers
            // are not able to process the failed message event
            boolean processed = outboxStateService.tryMarkProcessing(event.getId());

            if(processed)
            {
                return;
            }

            messagingTemplate.convertAndSend(
                    event.getDest(),
                    event.getPayload()
            );

            outboxStateService.markPublished(event.getId());

        } catch (Exception e) {

            outboxStateService.markPendingAgain(event.getId());

            throw e;
        }
    }
}

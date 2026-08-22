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

import java.util.List;

@Component
public class OutboxDispatcher {

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

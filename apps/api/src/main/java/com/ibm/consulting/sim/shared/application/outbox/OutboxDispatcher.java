package com.ibm.consulting.sim.shared.application.outbox;

import com.ibm.consulting.sim.shared.application.kafka.KafkaEventPublisher;
import com.ibm.consulting.sim.shared.domain.outbox.OutboxEvent;
import com.ibm.consulting.sim.shared.domain.outbox.OutboxEventRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

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


    private static final Logger log =
            LoggerFactory.getLogger(
                    OutboxDispatcher.class
            );

    private final OutboxClaimService claimService;

    private final OutboxEventRepository repository;

    private final OutboxStateService stateService;

    private final KafkaEventPublisher publisher;

    public OutboxDispatcher(OutboxClaimService claimService, OutboxEventRepository repository, OutboxStateService stateService, KafkaEventPublisher publisher) {
        this.claimService = claimService;
        this.repository = repository;
        this.stateService = stateService;
        this.publisher = publisher;
    }


    @Scheduled(
            fixedDelayString =
                    "${app.kafka.outbox.poll-delay-ms:200}"
    )
    public void dispatch() {

        UUID claimToken = UUID.randomUUID();
        List<UUID> ids =
                claimService.claimBatch(100 , claimToken);

        for (UUID id : ids) {

            OutboxEvent event =
                    repository.findById(id)
                            .orElseThrow();

            try {

                /*
                 * Same method handles
                 *
                 * ORDERED and UNORDERED.
                 *
                 * KafkaEventPublisher chooses
                 * the correct Kafka key.
                 */
                publisher.publish(
                        event.getTopic(),
                        event.toEnvelope()
                ).join();

                stateService.markPublished(
                        id,
                        claimToken
                );

            } catch (Exception ex) {

                stateService.markPendingAgain(
                        id,
                        claimToken
                );

                log.warn(
                        "Outbox publication failed: eventId={}, eventType={}, topic={}",
                        event.getId(),
                        event.getEventType(),
                        event.getTopic(),
                        ex
                );
            }
        }
    }
}
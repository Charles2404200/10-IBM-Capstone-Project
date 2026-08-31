
package com.ibm.consulting.sim.shared.application.kafka;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.apache.kafka.clients.consumer.ConsumerRecord;

// ConsumerRecord
// ├── topic      = "notifications"
// ├── partition  = 2
// ├── offset     = 57
// ├── key        = "LEARNER"
// └── value      = EventEnvelope(...)

@Component
public class KafkaDltConsumer {

    private static final Logger log =
            LoggerFactory.getLogger(
                    KafkaDltConsumer.class
            );

    @KafkaListener(
            topics =
                    "${app.kafka.notifications.dlt.topic-name:notifications.DLT}",

            groupId =
                    "${app.kafka.notifications.dlt.consumer.group-id:notification-dlt-monitor}",

            concurrency =
                    "${app.kafka.notifications.dlt.consumer.concurrency:1}",

            containerFactory =
                    "kafkaDltListenerContainerFactory"
    )
    public void consume(
            ConsumerRecord<
                    String,
                    byte[]
                    > record
    ) {
        /*
         * Never log or deserialize the payload. Invalid serialized data is a
         * valid DLT use case; metadata and Kafka headers remain available to
         * operational tooling.
         */
        log.error(
                "Kafka DLT record: topic={}, partition={}, offset={}, key={}, payloadBytes={}",
                record.topic(),
                record.partition(),
                record.offset(),
                record.key(),
                record.value() == null ? 0 : record.value().length
        );
    }
}

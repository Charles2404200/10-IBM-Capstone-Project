
import com.ibm.consulting.sim.shared.domain.EventEnvelope;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
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
            topicPattern =
                    ".*\\.DLT",

            groupId =
                    "${app.kafka.dlt.consumer.group-id}",

            containerFactory =
                    "kafkaListenerContainerFactory"
    )
    public void consume(
            ConsumerRecord<
                    String,
                    EventEnvelope
                    > record
    ) {

        EventEnvelope event =
                record.value();

        /*
         * Never log payload.
         */
        log.error(
                "Kafka DLT event: eventId={}, eventType={}, topic={}, partition={}, offset={}",
                event != null
                        ? event.eventId()
                        : null,

                event != null
                        ? event.eventType()
                        : null,

                record.topic(),
                record.partition(),
                record.offset()
        );
    }
}
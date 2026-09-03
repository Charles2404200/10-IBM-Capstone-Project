package com.ibm.consulting.sim.admin.application;


import com.ibm.consulting.sim.shared.application.kafka.KafkaEventProcessor;
import com.ibm.consulting.sim.shared.domain.outbox.EventEnvelope;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

@Component
public class NotificationConsumer {

    private final KafkaEventProcessor processor;

    public NotificationConsumer(KafkaEventProcessor processor)
    {
        this.processor = processor;
    }

    @KafkaListener(
            topics = "${app.kafka.notifications.topic.name}",
            groupId = "${app.kafka.notifications.consumer.group-id}",
            concurrency = "${app.kafka.notifications.consumer.concurrency}",
            containerFactory = "eventEnvelopeKafkaListenerContainerFactory"
    )
    public void consume(
            ConsumerRecord<String, EventEnvelope> record,

            @Header(KafkaHeaders.GROUP_ID)
            String consumerGroup
    ) {

        processor.process(
                consumerGroup,
                record
        );
    }
}

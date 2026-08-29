package com.ibm.consulting.sim.shared.application.kafka;

import com.ibm.consulting.sim.shared.config.KafkaConsumerConfig;
import com.ibm.consulting.sim.shared.config.KafkaProducerConfig;
import com.ibm.consulting.sim.shared.domain.outbox.EventEnvelope;
import com.ibm.consulting.sim.shared.domain.outbox.OrderingMode;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.kafka.KafkaProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.ssl.SslBundles;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.mock;

@SpringJUnitConfig(classes = {
        KafkaProducerConfig.class,
        KafkaConsumerConfig.class,
        KafkaEventPublisher.class,
        KafkaProducerConsumerIntegrationTest.TestConfiguration.class
})
@EmbeddedKafka(
        partitions = 1,
        topics = KafkaProducerConsumerIntegrationTest.TOPIC,
        bootstrapServersProperty = "spring.kafka.bootstrap-servers"
)
@TestPropertySource(properties = {
        "spring.kafka.consumer.auto-offset-reset=earliest",
        "app.kafka.producer.delivery-timeout-ms=120000",
        "app.kafka.producer.request-timeout-ms=30000",
        "app.kafka.producer.retry-backoff-ms=100",
        "app.kafka.producer.retries=3",
        "app.kafka.consumer.retry-backoff-ms=100",
        "app.kafka.consumer.max-retries=1"
})
class KafkaProducerConsumerIntegrationTest {

    static final String TOPIC = "producer-consumer-integration-test";
    private static final String CONSUMER_GROUP =
            "producer-consumer-integration-test-group";

    private final KafkaEventPublisher publisher;
    private final TestEnvelopeConsumer consumer;

    @Autowired
    KafkaProducerConsumerIntegrationTest(
            KafkaEventPublisher publisher,
            TestEnvelopeConsumer consumer
    ) {
        this.publisher = publisher;
        this.consumer = consumer;
    }

    @Test
    void publishesAndConsumesEventEnvelopeThroughConfiguredKafkaComponents()
            throws Exception {
        UUID eventId = UUID.randomUUID();
        Instant occurredAt = Instant.now();
        String orderingKey = "notifications:LEARNER";
        EventEnvelope expected = new EventEnvelope(
                eventId,
                "NOTIFICATION_PUBLISHED",
                1,
                OrderingMode.ORDERED,
                orderingKey,
                42L,
                occurredAt,
                "{\"topicName\":\"Integration test\"}"
        );

        publisher.publish(TOPIC, expected).join();

        ConsumerRecord<String, EventEnvelope> consumed =
                consumer.poll(10, TimeUnit.SECONDS);

        assertNotNull(consumed, "Kafka listener did not receive the event");
        assertEquals(orderingKey, consumed.key());
        assertEquals(TOPIC, consumed.topic());
        assertEquals(0, consumed.partition());
        assertEquals(expected, consumed.value());
    }

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(KafkaProperties.class)
    static class TestConfiguration {

        @Bean
        SslBundles sslBundles() {
            return mock(SslBundles.class);
        }

        @Bean
        TestEnvelopeConsumer testEnvelopeConsumer() {
            return new TestEnvelopeConsumer();
        }
    }

    static class TestEnvelopeConsumer {

        private final BlockingQueue<ConsumerRecord<String, EventEnvelope>> records =
                new LinkedBlockingQueue<>();

        @KafkaListener(
                topics = TOPIC,
                groupId = CONSUMER_GROUP,
                containerFactory = "kafkaListenerContainerFactory"
        )
        void consume(ConsumerRecord<String, EventEnvelope> record) {
            records.add(record);
        }

        ConsumerRecord<String, EventEnvelope> poll(
                long timeout,
                TimeUnit unit
        ) throws InterruptedException {
            return records.poll(timeout, unit);
        }
    }
}

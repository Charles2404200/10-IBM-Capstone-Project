package com.ibm.consulting.sim.admin.application;

import com.ibm.consulting.sim.shared.application.kafka.KafkaEventProcessor;
import com.ibm.consulting.sim.shared.application.kafka.KafkaEventPublisher;
import com.ibm.consulting.sim.shared.config.KafkaConsumerConfig;
import com.ibm.consulting.sim.shared.config.KafkaProducerConfig;
import com.ibm.consulting.sim.shared.config.KafkaProducerProperties;
import com.ibm.consulting.sim.shared.config.KafkaConsumerReliabilityProperties;
import com.ibm.consulting.sim.shared.application.kafka.KafkaDltMetrics;
import com.ibm.consulting.sim.admin.infrastructure.NotificationKafkaProperties;
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
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

// we are having embedded kafka
// only for kafka testing only
// since it imported from
// spring kafka test

@SpringJUnitConfig(classes = {
        KafkaProducerConfig.class,
        KafkaConsumerConfig.class,
        KafkaEventPublisher.class,
        NotificationConsumer.class,
        NotificationConsumerRetryIntegrationTest.TestConfiguration.class
})
@EmbeddedKafka(
        partitions = 1,
        topics = NotificationConsumerRetryIntegrationTest.TOPIC,
        bootstrapServersProperty = "spring.kafka.bootstrap-servers"
)
@TestPropertySource(properties = {
        "spring.kafka.consumer.auto-offset-reset=earliest",
        "app.kafka.notifications.topic.name=notification-retry-integration-test",
        "app.kafka.notifications.consumer.group-id=notification-retry-integration-test-group",
        "app.kafka.notifications.consumer.concurrency=1",
        "app.kafka.producer.delivery-timeout=120s",
        "app.kafka.producer.request-timeout=30s",
        "app.kafka.producer.retry-backoff=100ms",
        "app.kafka.producer.retries=3",
        "app.kafka.consumer.retry-backoff=100ms",
        "app.kafka.consumer.max-retries=2",
        "app.kafka.notifications.topic.partitions=1",
        "app.kafka.notifications.topic.replicas=1",
        "app.kafka.notifications.dlt.topic-name=notification-retry-integration-test.DLT",
        "app.kafka.notifications.dlt.consumer.group-id=notification-dlt-monitor",
        "app.kafka.notifications.dlt.consumer.concurrency=1"
})
class NotificationConsumerRetryIntegrationTest {

    static final String TOPIC = "notification-retry-integration-test";
    private static final String CONSUMER_GROUP =
            "notification-retry-integration-test-group";

    private final KafkaEventPublisher publisher;
    private final KafkaEventProcessor processor;

    @Autowired
    NotificationConsumerRetryIntegrationTest(
            KafkaEventPublisher publisher,
            KafkaEventProcessor processor
    ) {
        this.publisher = publisher;
        this.processor = processor;
    }

    @Test
    void retriesNotificationWhenInitialProcessingAttemptFails() throws Exception {
        UUID eventId = UUID.randomUUID();
        String orderingKey = "notifications:LEARNER";
        EventEnvelope notification = new EventEnvelope(
                eventId,
                "NOTIFICATION_PUBLISHED",
                1,
                OrderingMode.ORDERED,
                orderingKey,
                1L,
                Instant.now(),
                "{\"topicName\":\"Retry test\",\"message\":\"Retry me\"}"
        );
        AtomicInteger attempts = new AtomicInteger();
        AtomicReference<ConsumerRecord<String, EventEnvelope>> processedRecord =
                new AtomicReference<>();
        CountDownLatch processed = new CountDownLatch(1);

        // Simulate a transient business-processing failure. The listener's
        // DefaultErrorHandler should seek and deliver the same record again.
        doAnswer(invocation -> {
            int attempt = attempts.incrementAndGet();
            if (attempt == 1) {
                throw new IllegalStateException("Temporary notification failure");
            }

            processedRecord.set(invocation.getArgument(1));
            processed.countDown();
            return null;
        }).when(processor).process(eq(CONSUMER_GROUP), any());

        publisher.publish(TOPIC, notification).join();

        assertTrue(
                processed.await(10, TimeUnit.SECONDS),
                "Notification was not processed successfully after retry"
        );
        verify(processor, times(2)).process(eq(CONSUMER_GROUP), any());
        assertEquals(2, attempts.get());

        ConsumerRecord<String, EventEnvelope> retriedRecord = processedRecord.get();
        assertNotNull(retriedRecord);
        assertEquals(orderingKey, retriedRecord.key());
        assertEquals(eventId, retriedRecord.value().eventId());
    }

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties({
            KafkaProperties.class,
            KafkaProducerProperties.class,
            KafkaConsumerReliabilityProperties.class,
            NotificationKafkaProperties.class
    })
    static class TestConfiguration {

        @Bean
        SslBundles sslBundles() {
            return mock(SslBundles.class);
        }

        @Bean
        KafkaEventProcessor kafkaEventProcessor() {
            return mock(KafkaEventProcessor.class);
        }

        @Bean
        KafkaDltMetrics kafkaDltMetrics() {
            return mock(KafkaDltMetrics.class);
        }
    }
}

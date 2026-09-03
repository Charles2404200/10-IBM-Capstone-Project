package com.ibm.consulting.sim.shared.application.kafka;

import com.ibm.consulting.sim.admin.application.NotificationConsumer;
import com.ibm.consulting.sim.admin.infrastructure.NotificationKafkaProperties;
import com.ibm.consulting.sim.shared.config.KafkaConsumerConfig;
import com.ibm.consulting.sim.shared.config.KafkaConsumerReliabilityProperties;
import com.ibm.consulting.sim.shared.config.KafkaProducerConfig;
import com.ibm.consulting.sim.shared.config.KafkaProducerProperties;
import com.ibm.consulting.sim.shared.domain.outbox.EventEnvelope;
import com.ibm.consulting.sim.shared.domain.outbox.OrderingMode;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.serialization.ByteArraySerializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.kafka.KafkaProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.ssl.SslBundles;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.ApplicationContext;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.kafka.test.EmbeddedKafkaBroker;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.kafka.test.utils.KafkaTestUtils;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.LockSupport;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@SpringJUnitConfig(classes = {
        KafkaProducerConfig.class,
        KafkaConsumerConfig.class,
        KafkaEventPublisher.class,
        KafkaDltConsumer.class,
        KafkaDltMetrics.class,
        NotificationConsumer.class,
        KafkaDeadLetterIntegrationTest.TestConfiguration.class
})
@EmbeddedKafka(
        partitions = 1,
        topics = {
                KafkaDeadLetterIntegrationTest.SOURCE_TOPIC,
                KafkaDeadLetterIntegrationTest.DLT_TOPIC
        },
        bootstrapServersProperty = "spring.kafka.bootstrap-servers"
)
@TestPropertySource(properties = {
        "spring.kafka.consumer.auto-offset-reset=earliest",
        "app.kafka.producer.delivery-timeout=30s",
        "app.kafka.producer.request-timeout=10s",
        "app.kafka.producer.retry-backoff=50ms",
        "app.kafka.producer.retries=3",
        "app.kafka.consumer.retry-backoff=50ms",
        "app.kafka.consumer.max-retries=2",
        "app.kafka.notifications.topic.name=notification-dlt-integration-test",
        "app.kafka.notifications.topic.partitions=1",
        "app.kafka.notifications.topic.replicas=1",
        "app.kafka.notifications.consumer.group-id=notification-dlt-source-group",
        "app.kafka.notifications.consumer.concurrency=1",
        "app.kafka.notifications.dlt.topic-name=notification-dlt-integration-test.DLT",
        "app.kafka.notifications.dlt.consumer.group-id=notification-dlt-production-group",
        "app.kafka.notifications.dlt.consumer.concurrency=1"
})
class KafkaDeadLetterIntegrationTest {

    static final String SOURCE_TOPIC = "notification-dlt-integration-test";
    static final String DLT_TOPIC = SOURCE_TOPIC + ".DLT";
    private static final String SOURCE_GROUP = "notification-dlt-source-group";

    private final KafkaEventPublisher publisher;
    private final KafkaTemplate<String, byte[]> rawTemplate;
    private final KafkaEventProcessor processor;
    private final DltProbe probe;
    private final MeterRegistry meterRegistry;
    private final ApplicationContext applicationContext;
    private final FailingDltProbe failingDltProbe;

    @Autowired
    KafkaDeadLetterIntegrationTest(
            KafkaEventPublisher publisher,
            @Qualifier("rawKafkaTemplate") KafkaTemplate<String, byte[]> rawTemplate,
            KafkaEventProcessor processor,
            DltProbe probe,
            MeterRegistry meterRegistry,
            ApplicationContext applicationContext,
            FailingDltProbe failingDltProbe) {
        this.publisher = publisher;
        this.rawTemplate = rawTemplate;
        this.processor = processor;
        this.probe = probe;
        this.meterRegistry = meterRegistry;
        this.applicationContext = applicationContext;
        this.failingDltProbe = failingDltProbe;
    }

    @Test
    void productionKafkaReliabilityBeansAreRegistered() {
        assertNotNull(applicationContext.getBean(KafkaDltConsumer.class));
        assertNotNull(applicationContext.getBean(DeadLetterPublishingRecoverer.class));
        assertNotNull(applicationContext.getBean("kafkaErrorHandler", DefaultErrorHandler.class));
        assertNotNull(applicationContext.getBean(
                "eventEnvelopeKafkaListenerContainerFactory",
                ConcurrentKafkaListenerContainerFactory.class));
        assertNotNull(applicationContext.getBean(
                "kafkaDltListenerContainerFactory",
                ConcurrentKafkaListenerContainerFactory.class));
    }

    @BeforeEach
    void resetTestState() {
        reset(processor);
        probe.clear();
    }

    @Test
    void listenerFailureRetriesThenReachesRawDltConsumerWithDiagnosticHeaders() throws Exception {
        AtomicInteger attempts = new AtomicInteger();
        doAnswer(invocation -> {
            attempts.incrementAndGet();
            throw new IllegalStateException("temporary downstream outage");
        }).when(processor).process(eq(SOURCE_GROUP), any());
        double receivedBefore = dltReceivedCount();

        publisher.publish(SOURCE_TOPIC, envelope(UUID.randomUUID())).join();

        ConsumerRecord<String, byte[]> dlt = probe.poll(15, TimeUnit.SECONDS);
        assertNotNull(dlt, "failed listener record did not reach the DLT");
        assertEquals(3, attempts.get(), "initial attempt plus two bounded retries expected");
        verify(processor, times(3)).process(eq(SOURCE_GROUP), any());
        assertDltHeaders(dlt);
        assertTrue(dlt.value().length > 0);
        assertTrue(awaitDltMetric(receivedBefore + 1, 10, TimeUnit.SECONDS),
                "production KafkaDltConsumer did not receive the record");
    }

    @Test
    void malformedJsonReachesByteArrayDltAndDoesNotBlockFollowingValidRecord() throws Exception {
        byte[] poison = "{not-valid-json".getBytes(StandardCharsets.UTF_8);
        CountDownLatch validProcessed = new CountDownLatch(1);
        UUID validId = UUID.randomUUID();
        doAnswer(invocation -> {
            ConsumerRecord<String, EventEnvelope> record = invocation.getArgument(1);
            if (record.value().eventId().equals(validId)) {
                validProcessed.countDown();
            }
            return null;
        }).when(processor).process(eq(SOURCE_GROUP), any());
        double receivedBefore = dltReceivedCount();

        rawTemplate.send(SOURCE_TOPIC, "poison-key", poison).join();

        ConsumerRecord<String, byte[]> dlt = probe.poll(15, TimeUnit.SECONDS);
        assertNotNull(dlt, "malformed JSON did not reach the DLT");
        assertArrayEquals(poison, dlt.value(),
                "DLT must preserve the unreadable source bytes exactly");
        assertDltHeaders(dlt);
        assertTrue(awaitDltMetric(receivedBefore + 1, 10, TimeUnit.SECONDS),
                "production KafkaDltConsumer did not receive malformed bytes");

        publisher.publish(SOURCE_TOPIC, envelope(validId)).join();
        assertTrue(validProcessed.await(10, TimeUnit.SECONDS),
                "poison record blocked the following valid record");
    }

    @Test
    void dltHandlerFailureIsHandledOnceWithoutRecursiveDeadLettering() throws Exception {
        double failuresBefore = dltHandlingFailureCount();
        failingDltProbe.arm();

        rawTemplate.send(DLT_TOPIC, "terminal-poison", new byte[]{0x01}).join();

        assertTrue(failingDltProbe.awaitFirstAttempt(10, TimeUnit.SECONDS));
        assertTrue(awaitMetric(
                "consulting.kafka.dlt.handling.failure",
                failuresBefore + 1,
                10,
                TimeUnit.SECONDS));
        assertTrue(failingDltProbe.noSecondAttemptWithin(750, TimeUnit.MILLISECONDS),
                "DLT handler was retried or recursively dead-lettered");
    }

    private void assertDltHeaders(ConsumerRecord<String, byte[]> record) {
        assertNotNull(record.headers().lastHeader(KafkaHeaders.DLT_ORIGINAL_TOPIC));
        assertNotNull(record.headers().lastHeader(KafkaHeaders.DLT_ORIGINAL_PARTITION));
        assertNotNull(record.headers().lastHeader(KafkaHeaders.DLT_ORIGINAL_OFFSET));
        assertNotNull(record.headers().lastHeader(KafkaHeaders.DLT_EXCEPTION_FQCN));
        assertNotNull(record.headers().lastHeader(KafkaHeaders.DLT_EXCEPTION_MESSAGE));
    }

    private boolean awaitDltMetric(double expected, long timeout, TimeUnit unit) {
        return awaitMetric("consulting.kafka.dlt.received", expected, timeout, unit);
    }

    private boolean awaitMetric(String name, double expected, long timeout, TimeUnit unit) {
        long deadline = System.nanoTime() + unit.toNanos(timeout);
        while (System.nanoTime() < deadline) {
            if (meterRegistry.counter(name).count() >= expected) {
                return true;
            }
            LockSupport.parkNanos(TimeUnit.MILLISECONDS.toNanos(25));
        }
        return meterRegistry.counter(name).count() >= expected;
    }

    private double dltReceivedCount() {
        return meterRegistry.counter("consulting.kafka.dlt.received").count();
    }

    private double dltHandlingFailureCount() {
        return meterRegistry.counter("consulting.kafka.dlt.handling.failure").count();
    }

    private EventEnvelope envelope(UUID eventId) {
        return new EventEnvelope(
                eventId,
                "NOTIFICATION_PUBLISHED",
                1,
                OrderingMode.ORDERED,
                "notifications:LEARNER",
                1L,
                Instant.now(),
                "{\"topicName\":\"DLT integration test\"}"
        );
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
        MeterRegistry meterRegistry() {
            return new SimpleMeterRegistry();
        }

        @Bean
        DltProbe dltProbe() {
            return new DltProbe();
        }

        @Bean
        FailingDltProbe failingDltProbe() {
            return new FailingDltProbe();
        }

        @Bean("rawKafkaTemplate")
        KafkaTemplate<String, byte[]> rawKafkaTemplate(EmbeddedKafkaBroker broker) {
            var producer = new DefaultKafkaProducerFactory<String, byte[]>(
                    KafkaTestUtils.producerProps(broker),
                    new StringSerializer(),
                    new ByteArraySerializer()
            );
            return new KafkaTemplate<>(producer);
        }
    }

    static class DltProbe {
        private final BlockingQueue<ConsumerRecord<String, byte[]>> records =
                new LinkedBlockingQueue<>();

        @KafkaListener(
                topics = DLT_TOPIC,
                groupId = "notification-dlt-test-probe",
                containerFactory = "kafkaDltListenerContainerFactory"
        )
        void consume(ConsumerRecord<String, byte[]> record) {
            records.add(record);
        }

        ConsumerRecord<String, byte[]> poll(long timeout, TimeUnit unit)
                throws InterruptedException {
            return records.poll(timeout, unit);
        }

        void clear() {
            records.clear();
        }
    }

    static class FailingDltProbe {
        private final AtomicBoolean armed = new AtomicBoolean();
        private final AtomicInteger attempts = new AtomicInteger();
        private volatile CountDownLatch firstAttempt = new CountDownLatch(1);
        private volatile CountDownLatch secondAttempt = new CountDownLatch(1);

        @KafkaListener(
                topics = DLT_TOPIC,
                groupId = "notification-dlt-failure-probe",
                containerFactory = "kafkaDltListenerContainerFactory"
        )
        void consume(ConsumerRecord<String, byte[]> record) {
            if (!armed.get()) {
                return;
            }
            int attempt = attempts.incrementAndGet();
            if (attempt == 1) {
                firstAttempt.countDown();
            } else {
                secondAttempt.countDown();
            }
            throw new IllegalStateException("simulated terminal DLT handler failure");
        }

        void arm() {
            attempts.set(0);
            firstAttempt = new CountDownLatch(1);
            secondAttempt = new CountDownLatch(1);
            armed.set(true);
        }

        boolean awaitFirstAttempt(long timeout, TimeUnit unit) throws InterruptedException {
            return firstAttempt.await(timeout, unit);
        }

        boolean noSecondAttemptWithin(long timeout, TimeUnit unit) throws InterruptedException {
            boolean retried = secondAttempt.await(timeout, unit);
            armed.set(false);
            return !retried && attempts.get() == 1;
        }
    }
}

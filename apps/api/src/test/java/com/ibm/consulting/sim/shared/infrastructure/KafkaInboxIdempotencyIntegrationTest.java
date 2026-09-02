package com.ibm.consulting.sim.shared.infrastructure;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ibm.consulting.sim.shared.application.kafka.KafkaEventContext;
import com.ibm.consulting.sim.shared.application.kafka.KafkaEventHandler;
import com.ibm.consulting.sim.shared.application.kafka.KafkaEventHandlerRegistry;
import com.ibm.consulting.sim.shared.application.kafka.KafkaEventProcessor;
import com.ibm.consulting.sim.shared.application.kafka.KafkaInboxMetrics;
import com.ibm.consulting.sim.shared.domain.outbox.EventEnvelope;
import com.ibm.consulting.sim.shared.domain.outbox.EventPriority;
import com.ibm.consulting.sim.shared.domain.outbox.OrderingMode;
import com.ibm.consulting.sim.shared.infrastructure.kafka.JPAKafkaInboxRepository;
import jakarta.persistence.EntityManager;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

/**
 * Verifies that the database uniqueness constraint and handler transaction
 * provide durable, per-consumer-group idempotency under sequential and
 * concurrent duplicate delivery.
 */
@DataJpaTest
@Import({
        JPAKafkaInboxRepository.class,
        KafkaEventProcessor.class,
        KafkaEventHandlerRegistry.class,
        KafkaInboxIdempotencyIntegrationTest.InboxTestConfiguration.class
})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers(disabledWithoutDocker = true)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class KafkaInboxIdempotencyIntegrationTest {

    private static final String EVENT_TYPE = "IDEMPOTENCY_TEST";
    private static final DockerImageName POSTGRES_IMAGE =
            DockerImageName.parse("pgvector/pgvector:pg16")
                    .asCompatibleSubstituteFor("postgres");

    @Container
    static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>(POSTGRES_IMAGE);

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @Autowired
    private KafkaEventProcessor processor;

    @Autowired
    private TestHandler handler;

    @Autowired
    private KafkaInboxMetrics metrics;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private PlatformTransactionManager transactionManager;

    private TransactionTemplate transaction;

    @BeforeEach
    void resetState() {
        transaction = new TransactionTemplate(transactionManager);
        handler.reset();
        org.mockito.Mockito.reset(metrics);
        transaction.executeWithoutResult(status ->
                entityManager.createQuery("DELETE FROM KafkaInboxEntity").executeUpdate());
    }

    @Test
    void duplicatePublicationExecutesBusinessSideEffectOncePerConsumerGroup() {
        UUID eventId = UUID.randomUUID();

        processor.process("group-a", record(eventId, 1));
        processor.process("group-a", record(eventId, 2));

        assertEquals(1, handler.invocations());
        assertEquals(1, inboxRows());
        verify(metrics).recordProcessed();
        verify(metrics).recordDuplicate();
    }

    @Test
    void sameEventIsProcessedIndependentlyByDifferentConsumerGroups() {
        UUID eventId = UUID.randomUUID();

        processor.process("group-a", record(eventId, 1));
        processor.process("group-b", record(eventId, 1));

        assertEquals(2, handler.invocations());
        assertEquals(2, inboxRows());
    }

    @Test
    void concurrentDuplicateDeliveryIsSerializedByCompositePrimaryKey() throws Exception {
        UUID eventId = UUID.randomUUID();
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);

        try (var executor = Executors.newFixedThreadPool(2)) {
            var first = executor.submit(() -> processConcurrently(eventId, 1, ready, start));
            var second = executor.submit(() -> processConcurrently(eventId, 2, ready, start));
            ready.await();
            start.countDown();
            first.get();
            second.get();
        }

        assertEquals(1, handler.invocations());
        assertEquals(1, inboxRows());
    }

    @Test
    void handlerFailureRollsBackInboxSoKafkaRetryCanSucceed() {
        UUID eventId = UUID.randomUUID();
        handler.failNext();

        assertThrows(
                IllegalStateException.class,
                () -> processor.process("group-a", record(eventId, 1))
        );
        assertEquals(0, inboxRows());

        processor.process("group-a", record(eventId, 1));

        assertEquals(2, handler.invocations());
        assertEquals(1, inboxRows());
    }

    private Void processConcurrently(
            UUID eventId,
            long offset,
            CountDownLatch ready,
            CountDownLatch start) throws InterruptedException {
        ready.countDown();
        start.await();
        processor.process("group-a", record(eventId, offset));
        return null;
    }

    private ConsumerRecord<String, EventEnvelope> record(UUID eventId, long offset) {
        EventEnvelope envelope = new EventEnvelope(
                eventId,
                EVENT_TYPE,
                1,
                OrderingMode.UNORDERED,
                null,
                null,
                EventPriority.NORMAL,
                Instant.now(),
                "{\"value\":\"payload\"}"
        );
        return new ConsumerRecord<>("test-events", 0, offset, eventId.toString(), envelope);
    }

    private long inboxRows() {
        return transaction.execute(status -> entityManager.createQuery(
                        "SELECT COUNT(inbox) FROM KafkaInboxEntity inbox", Long.class)
                .getSingleResult());
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class InboxTestConfiguration {

        @Bean
        ObjectMapper objectMapper() {
            return new ObjectMapper();
        }

        @Bean
        TestHandler testHandler() {
            return new TestHandler();
        }

        @Bean
        KafkaInboxMetrics kafkaInboxMetrics() {
            return mock(KafkaInboxMetrics.class);
        }
    }

    static class TestHandler implements KafkaEventHandler<TestPayload> {

        private final AtomicInteger invocations = new AtomicInteger();
        private final AtomicBoolean failNext = new AtomicBoolean();

        @Override
        public String eventType() {
            return EVENT_TYPE;
        }

        @Override
        public int schemaVersion() {
            return 1;
        }

        @Override
        public Class<TestPayload> payloadType() {
            return TestPayload.class;
        }

        @Override
        public void handle(TestPayload payload, KafkaEventContext context) {
            assertTrue(payload.value().equals("payload"));
            invocations.incrementAndGet();
            if (failNext.compareAndSet(true, false)) {
                throw new IllegalStateException("simulated business failure");
            }
        }

        void failNext() {
            failNext.set(true);
        }

        int invocations() {
            return invocations.get();
        }

        void reset() {
            invocations.set(0);
            failNext.set(false);
        }
    }

    record TestPayload(String value) {
    }
}

package com.ibm.consulting.sim.shared.infrastructure;

import com.ibm.consulting.sim.shared.domain.outbox.EventPriority;
import com.ibm.consulting.sim.shared.domain.outbox.OutboxEvent;
import com.ibm.consulting.sim.shared.domain.outbox.OutboxEventRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import jakarta.persistence.EntityManager;
import java.time.Duration;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DataJpaTest
@Import(JPAOutboxRepository.class)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers(disabledWithoutDocker = true)
@Transactional
class JpaOutboxPriorityRepositoryTest {

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
    private OutboxEventRepository repository;

    @Autowired
    private EntityManager entityManager;

    @Test
    void ranksOnlyEligibleHeadsAcrossIndependentStreams() {
        OutboxEvent learner = ordered("notifications:LEARNER", 10, EventPriority.NORMAL);
        OutboxEvent administrator = ordered("notifications:ADMIN", 8, EventPriority.CRITICAL);
        OutboxEvent aiHead = ordered("ai:meeting-123", 41, EventPriority.HIGH);
        OutboxEvent blockedAiCritical = ordered("ai:meeting-123", 42, EventPriority.CRITICAL);
        saveAndReload(learner, administrator, aiHead, blockedAiCritical);

        List<OutboxEvent> claimed = repository.findDispatchableForUpdate(10);

        assertEquals(
                List.of(administrator.getId(), aiHead.getId(), learner.getId()),
                claimed.stream().map(OutboxEvent::getId).toList()
        );
        assertTrue(claimed.stream().noneMatch(event -> event.getId().equals(blockedAiCritical.getId())));
    }

    @Test
    void laterCriticalEventWaitsUntilNormalPredecessorIsPublished() {
        OutboxEvent first = ordered("meeting:A", 1, EventPriority.NORMAL);
        OutboxEvent second = ordered("meeting:A", 2, EventPriority.CRITICAL);
        saveAndReload(first, second);

        assertEquals(
                List.of(first.getId()),
                repository.findDispatchableForUpdate(10).stream().map(OutboxEvent::getId).toList()
        );

        OutboxEvent persistedFirst = repository.findById(first.getId()).orElseThrow();
        persistedFirst.markProcessing(UUID.randomUUID());
        persistedFirst.markPublished();
        repository.save(persistedFirst);
        entityManager.flush();
        entityManager.clear();

        assertEquals(
                List.of(second.getId()),
                repository.findDispatchableForUpdate(10).stream().map(OutboxEvent::getId).toList()
        );
    }

    @Test
    void retryingPredecessorBlocksCriticalSuccessor() {
        OutboxEvent failed = ordered("meeting:B", 1, EventPriority.NORMAL);
        failed.markProcessing(UUID.randomUUID());
        failed.markRetry(Duration.ofMinutes(5));
        OutboxEvent critical = ordered("meeting:B", 2, EventPriority.CRITICAL);
        saveAndReload(failed, critical);

        assertTrue(repository.findDispatchableForUpdate(10).isEmpty());
    }

    @Test
    void unorderedEventsAreRankedByPriorityThenAge() {
        OutboxEvent normal = unordered(EventPriority.NORMAL);
        OutboxEvent critical = unordered(EventPriority.CRITICAL);
        OutboxEvent high = unordered(EventPriority.HIGH);
        saveAndReload(normal, critical, high);

        assertEquals(
                List.of(critical.getId(), high.getId(), normal.getId()),
                repository.findDispatchableForUpdate(10).stream().map(OutboxEvent::getId).toList()
        );
    }

    private void saveAndReload(OutboxEvent... events) {
        for (OutboxEvent event : events) {
            repository.save(event);
            entityManager.flush();
        }
        entityManager.clear();
    }

    private OutboxEvent ordered(String key, long sequence, EventPriority priority) {
        return OutboxEvent.ordered(
                UUID.randomUUID(), "events", "TEST", 1, key, sequence, priority, "{}"
        );
    }

    private OutboxEvent unordered(EventPriority priority) {
        return OutboxEvent.unordered(
                UUID.randomUUID(), "events", "TEST", 1, priority, "{}"
        );
    }
}

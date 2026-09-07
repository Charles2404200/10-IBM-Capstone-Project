package com.ibm.consulting.sim.engagement.infrastructure;

import com.ibm.consulting.sim.engagement.application.EngagementResponse;
import com.ibm.consulting.sim.engagement.application.RetryEngagementUseCase;
import com.ibm.consulting.sim.engagement.domain.Engagement;
import com.ibm.consulting.sim.engagement.domain.EngagementRepository;
import com.ibm.consulting.sim.engagement.domain.EngagementState;
import com.ibm.consulting.sim.identity.domain.User;
import com.ibm.consulting.sim.identity.domain.UserRole;
import com.ibm.consulting.sim.lead.domain.Lead;
import com.ibm.consulting.sim.lead.domain.LeadDifficulty;
import com.ibm.consulting.sim.scenario.domain.Persona;
import com.ibm.consulting.sim.scenario.domain.Scenario;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.cache.CacheManager;
import org.springframework.cache.concurrent.ConcurrentMapCache;
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

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@DataJpaTest
@Import(JpaEngagementRepository.class)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers(disabledWithoutDocker = true)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class EngagementRetryConcurrencyIntegrationTest {

    @Container
    static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>(
            DockerImageName.parse("pgvector/pgvector:pg16").asCompatibleSubstituteFor("postgres"));

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @Autowired EntityManager entityManager;
    @Autowired EngagementRepository engagementRepository;
    @Autowired PlatformTransactionManager transactionManager;
    @MockBean CacheManager cacheManager;

    @BeforeEach
    void configureCaches() {
        ConcurrentMapCache cache = new ConcurrentMapCache("test");
        when(cacheManager.getCache(anyString())).thenReturn(cache);
    }

    @Test
    void concurrentRetriesCreateOnlyOneWorkspaceForTheFailedEngagement() throws Exception {
        TestIds ids = inTransaction(this::persistFailedEngagement);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch retrySavesReached = new CountDownLatch(2);
        EngagementRepository gatedRepository = gateRetrySaves(engagementRepository, retrySavesReached);

        try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
            Future<EngagementResponse> first = executor.submit(
                    () -> executeRetry(start, gatedRepository, ids.failedEngagementId(), ids.userId()));
            Future<EngagementResponse> second = executor.submit(
                    () -> executeRetry(start, gatedRepository, ids.failedEngagementId(), ids.userId()));
            start.countDown();

            EngagementResponse firstResponse = first.get(10, TimeUnit.SECONDS);
            EngagementResponse secondResponse = second.get(10, TimeUnit.SECONDS);

            List<Engagement> retries = inTransaction(() -> engagementRepository.findByUserId(ids.userId()).stream()
                    .filter(candidate -> ids.failedEngagementId().equals(candidate.getRetryOfEngagementId()))
                    .toList());
            assertThat(retries).hasSize(1);
            assertThat(firstResponse.id()).isEqualTo(secondResponse.id());
        }
    }

    private EngagementResponse executeRetry(CountDownLatch start, EngagementRepository repository,
                                              UUID failedEngagementId, UUID userId) throws Exception {
        assertThat(start.await(5, TimeUnit.SECONDS)).isTrue();
        return inTransaction(() -> new RetryEngagementUseCase(repository).execute(failedEngagementId, userId));
    }

    private EngagementRepository gateRetrySaves(EngagementRepository delegate, CountDownLatch savesReached) {
        return new EngagementRepository() {
            @Override
            public Engagement save(Engagement engagement) {
                if (engagement.getRetryOfEngagementId() != null) {
                    savesReached.countDown();
                    awaitPeer(savesReached);
                }
                return delegate.save(engagement);
            }

            @Override public List<Engagement> findAll() { return delegate.findAll(); }
            @Override public Optional<Engagement> findById(UUID id) { return delegate.findById(id); }
            @Override public List<Engagement> findByUserId(UUID userId) { return delegate.findByUserId(userId); }
            @Override public List<Engagement> findDashboardByUserId(UUID userId) {
                return delegate.findDashboardByUserId(userId);
            }
            @Override public Optional<Engagement> findByIdAndUserId(UUID id, UUID userId) {
                return delegate.findByIdAndUserId(id, userId);
            }
            @Override public Optional<Engagement> findByIdAndUserIdForUpdate(UUID id, UUID userId) {
                return delegate.findByIdAndUserIdForUpdate(id, userId);
            }
        };
    }

    private void awaitPeer(CountDownLatch latch) {
        try {
            latch.await(1, TimeUnit.SECONDS);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while coordinating concurrent retries", exception);
        }
    }

    private TestIds persistFailedEngagement() {
        User user = User.create("retry-user@example.com", "hash", "Retry User", UserRole.LEARNER);
        Scenario scenario = Scenario.create("Retry scenario", "Technology", "Scenario", 3);
        Persona persona = Persona.create(scenario, "Client", "CIO", "Example Co", "Direct", "Risk",
                "Budget", "Delivery");
        Lead lead = Lead.create(scenario.getId(), "Example Co", "Technology", "Modernisation", LeadDifficulty.MEDIUM);
        entityManager.persist(user);
        entityManager.persist(scenario);
        entityManager.persist(persona);
        entityManager.persist(lead);

        Engagement failed = Engagement.start(user.getId(), scenario.getId(), persona.getId());
        failed.selectLead(lead.getId());
        failed.transitionTo(EngagementState.HYPOTHESIS_READY, "Hypothesis ready");
        failed.transitionTo(EngagementState.OUTREACHING, "Outreach started");
        failed.transitionTo(EngagementState.MEETING_SECURED, "Meeting secured");
        failed.transitionTo(EngagementState.PREPARING, "Preparation started");
        failed.transitionTo(EngagementState.IN_MEETING, "Meeting started");
        failed.transitionTo(EngagementState.MEETING_FAILED, "Meeting failed");
        entityManager.persist(failed);
        entityManager.flush();
        return new TestIds(user.getId(), failed.getId());
    }

    private <T> T inTransaction(java.util.concurrent.Callable<T> work) {
        return new TransactionTemplate(transactionManager).execute(status -> {
            try {
                return work.call();
            } catch (RuntimeException exception) {
                throw exception;
            } catch (Exception exception) {
                throw new IllegalStateException(exception);
            }
        });
    }

    private record TestIds(UUID userId, UUID failedEngagementId) {}
}

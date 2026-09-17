package com.ibm.consulting.sim.achievement.infrastructure;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ibm.consulting.sim.achievement.application.AchievementEvaluationService;
import com.ibm.consulting.sim.achievement.application.AchievementFactSheetBuilder;
import com.ibm.consulting.sim.achievement.application.AchievementRuleMapper;
import com.ibm.consulting.sim.achievement.application.ConditionNode;
import com.ibm.consulting.sim.achievement.domain.Achievement;
import com.ibm.consulting.sim.achievement.domain.AchievementFactSheet;
import com.ibm.consulting.sim.achievement.domain.AchievementRepository;
import com.ibm.consulting.sim.achievement.domain.ConditionType;
import com.ibm.consulting.sim.achievement.domain.UserAchievement;
import com.ibm.consulting.sim.achievement.domain.UserAchievementRepository;
import com.ibm.consulting.sim.identity.domain.User;
import com.ibm.consulting.sim.identity.domain.UserRole;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
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
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@DataJpaTest
@Import({JpaAchievementRepository.class, JpaUserAchievementRepository.class})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers(disabledWithoutDocker = true)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class AchievementUnlockConcurrencyIntegrationTest {

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
    @Autowired AchievementRepository achievementRepository;
    @Autowired UserAchievementRepository unlockRepository;
    @Autowired PlatformTransactionManager transactionManager;

    @Test
    void concurrentEvaluationCreatesOneUnlockAndOneNewlyUnlockedResult() throws Exception {
        AchievementRuleCodec codec = new AchievementRuleCodec(new ObjectMapper());
        String ruleJson = codec.encode(ConditionNode.leaf(ConditionType.MIN_ENGAGEMENTS_COMPLETED, null, 0));
        UUID userId = inTransaction(() -> {
            User user = User.create("achievement-" + UUID.randomUUID() + "@example.com", "hash", "Learner", UserRole.LEARNER);
            entityManager.persist(user);
            entityManager.persist(Achievement.create("First step", "Complete one task", "trophy", ruleJson));
            entityManager.flush();
            return user.getId();
        });

        CountDownLatch existenceChecks = new CountDownLatch(2);
        UserAchievementRepository gatedUnlocks = gateExistenceChecks(unlockRepository, existenceChecks);
        AchievementFactSheetBuilder facts = mock(AchievementFactSheetBuilder.class);
        when(facts.build(userId)).thenReturn(AchievementFactSheet.empty());
        AchievementEvaluationService service = new AchievementEvaluationService(
                achievementRepository, gatedUnlocks, facts,
                codec, new AchievementRuleMapper());

        List<List<Achievement>> results = runConcurrently(
                () -> service.evaluateForUser(userId),
                () -> service.evaluateForUser(userId));

        List<UserAchievement> persisted = inTransaction(() -> unlockRepository.findByUserId(userId));
        assertThat(persisted).hasSize(1);
        assertThat(results.stream().mapToInt(List::size).sum()).isEqualTo(1);
    }

    private UserAchievementRepository gateExistenceChecks(UserAchievementRepository delegate,
                                                            CountDownLatch checksReached) {
        return new UserAchievementRepository() {
            @Override public UserAchievement save(UserAchievement unlock) { return delegate.save(unlock); }
            @Override public List<UserAchievement> findByUserId(UUID userId) { return delegate.findByUserId(userId); }
            @Override public boolean existsByUserIdAndAchievementId(UUID userId, UUID achievementId) {
                boolean exists = delegate.existsByUserIdAndAchievementId(userId, achievementId);
                checksReached.countDown();
                awaitPeer(checksReached);
                return exists;
            }
            @Override public boolean insertIfAbsent(UUID userId, UUID achievementId, Instant unlockedAt) {
                return delegate.insertIfAbsent(userId, achievementId, unlockedAt);
            }
        };
    }

    private List<List<Achievement>> runConcurrently(java.util.concurrent.Callable<List<Achievement>> firstCommand,
                                                     java.util.concurrent.Callable<List<Achievement>> secondCommand)
            throws Exception {
        CountDownLatch start = new CountDownLatch(1);
        try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
            Future<List<Achievement>> first = executor.submit(() -> execute(start, firstCommand));
            Future<List<Achievement>> second = executor.submit(() -> execute(start, secondCommand));
            start.countDown();
            return List.of(first.get(10, TimeUnit.SECONDS), second.get(10, TimeUnit.SECONDS));
        }
    }

    private List<Achievement> execute(CountDownLatch start,
                                      java.util.concurrent.Callable<List<Achievement>> command) throws Exception {
        assertThat(start.await(5, TimeUnit.SECONDS)).isTrue();
        return inTransaction(command);
    }

    private void awaitPeer(CountDownLatch latch) {
        try {
            assertThat(latch.await(5, TimeUnit.SECONDS)).isTrue();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(exception);
        }
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
}

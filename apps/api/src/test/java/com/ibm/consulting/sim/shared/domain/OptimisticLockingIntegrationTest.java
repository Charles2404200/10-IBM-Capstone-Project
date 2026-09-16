package com.ibm.consulting.sim.shared.domain;

import com.ibm.consulting.sim.scenario.domain.Scenario;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.OptimisticLockException;
import jakarta.persistence.RollbackException;
import org.hibernate.StaleObjectStateException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
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

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers(disabledWithoutDocker = true)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class OptimisticLockingIntegrationTest {

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
    @Autowired EntityManagerFactory entityManagerFactory;
    @Autowired PlatformTransactionManager transactionManager;

    @Test
    void stalePersistenceContextCannotOverwriteTheCommittedUpdate() {
        UUID scenarioId = new TransactionTemplate(transactionManager).execute(status -> {
            Scenario scenario = Scenario.create("Original", "Technology", "Original description", 3);
            entityManager.persist(scenario);
            entityManager.flush();
            return scenario.getId();
        });

        EntityManager firstContext = entityManagerFactory.createEntityManager();
        EntityManager staleContext = entityManagerFactory.createEntityManager();
        try {
            firstContext.getTransaction().begin();
            staleContext.getTransaction().begin();
            Scenario firstCopy = firstContext.find(Scenario.class, scenarioId);
            Scenario staleCopy = staleContext.find(Scenario.class, scenarioId);
            assertThat(firstCopy.getVersion()).isEqualTo(staleCopy.getVersion());

            firstCopy.updateMetadata("Committed winner", "Technology", "First update", 4);
            firstContext.getTransaction().commit();

            staleCopy.updateMetadata("Stale overwrite", "Technology", "Second update", 2);
            assertThatThrownBy(() -> staleContext.getTransaction().commit())
                    .isInstanceOfAny(OptimisticLockException.class, RollbackException.class)
                    .satisfies(throwable -> assertThat(hasOptimisticCause(throwable)).isTrue());
        } finally {
            if (firstContext.getTransaction().isActive()) firstContext.getTransaction().rollback();
            if (staleContext.getTransaction().isActive()) staleContext.getTransaction().rollback();
            firstContext.close();
            staleContext.close();
        }

        EntityManager verificationContext = entityManagerFactory.createEntityManager();
        try {
            Scenario committed = verificationContext.find(Scenario.class, scenarioId);
            assertThat(committed.getTitle()).isEqualTo("Committed winner");
            assertThat(committed.getDescription()).isEqualTo("First update");
            assertThat(committed.getDifficulty()).isEqualTo(4);
        } finally {
            verificationContext.close();
        }
    }

    private boolean hasOptimisticCause(Throwable failure) {
        Throwable current = failure;
        while (current != null) {
            if (current instanceof OptimisticLockException || current instanceof StaleObjectStateException) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }
}

package com.ibm.consulting.sim.shared.domain;

import com.ibm.consulting.sim.engagement.domain.Engagement;
import com.ibm.consulting.sim.engagement.domain.EngagementState;
import com.ibm.consulting.sim.identity.domain.User;
import com.ibm.consulting.sim.identity.domain.UserRole;
import com.ibm.consulting.sim.lead.domain.Lead;
import com.ibm.consulting.sim.lead.domain.LeadDifficulty;
import com.ibm.consulting.sim.scenario.domain.Persona;
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

    @Test
    void staleEngagementUpdateCannotOverwriteLifecycleOrCreateAnEvent() {
        EngagementData data = new TransactionTemplate(transactionManager).execute(status -> {
            User user = User.create(UUID.randomUUID() + "@example.com", "hash", "Learner", UserRole.LEARNER);
            Scenario scenario = Scenario.create("Concurrency", "Technology", "Scenario", 3);
            Persona persona = Persona.create(scenario, "Client", "CIO", "Example Co", "Direct",
                    "Risk", "Budget", "Delivery");
            Lead lead = Lead.create(scenario.getId(), "Example Co", "Technology",
                    "Opportunity", LeadDifficulty.MEDIUM);
            entityManager.persist(user);
            entityManager.persist(scenario);
            entityManager.persist(persona);
            entityManager.persist(lead);
            Engagement engagement = Engagement.start(user.getId(), scenario.getId(), persona.getId());
            entityManager.persist(engagement);
            entityManager.flush();
            return new EngagementData(engagement.getId(), lead.getId());
        });

        EntityManager winnerContext = entityManagerFactory.createEntityManager();
        EntityManager staleContext = entityManagerFactory.createEntityManager();
        try {
            winnerContext.getTransaction().begin();
            staleContext.getTransaction().begin();
            Engagement winner = winnerContext.find(Engagement.class, data.engagementId());
            Engagement stale = staleContext.find(Engagement.class, data.engagementId());
            assertThat(winner.getVersion()).isEqualTo(stale.getVersion());

            winner.selectLead(data.leadId());
            winnerContext.getTransaction().commit();

            stale.selectLead(data.leadId());
            assertThatThrownBy(() -> staleContext.getTransaction().commit())
                    .isInstanceOfAny(OptimisticLockException.class, RollbackException.class)
                    .satisfies(throwable -> assertThat(hasOptimisticCause(throwable)).isTrue());
        } finally {
            if (winnerContext.getTransaction().isActive()) winnerContext.getTransaction().rollback();
            if (staleContext.getTransaction().isActive()) staleContext.getTransaction().rollback();
            winnerContext.close();
            staleContext.close();
        }

        EntityManager verificationContext = entityManagerFactory.createEntityManager();
        try {
            Engagement committed = verificationContext.find(Engagement.class, data.engagementId());
            assertThat(committed.getState()).isEqualTo(EngagementState.CLIENT_INTELLIGENCE);
            assertThat(committed.getSelectedLeadId()).isEqualTo(data.leadId());
            assertThat(verificationContext.createQuery("""
                            select count(event) from EngagementEvent event
                            where event.engagement.id = :id and event.state = :state
                            """, Long.class)
                    .setParameter("id", data.engagementId())
                    .setParameter("state", EngagementState.CLIENT_INTELLIGENCE)
                    .getSingleResult()).isEqualTo(1L);
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

    private record EngagementData(UUID engagementId, UUID leadId) {}
}

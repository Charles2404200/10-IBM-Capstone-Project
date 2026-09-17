package com.ibm.consulting.sim.proposal.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ibm.consulting.sim.ai.application.AiOrchestrationService;
import com.ibm.consulting.sim.engagement.domain.Engagement;
import com.ibm.consulting.sim.engagement.domain.EngagementRepository;
import com.ibm.consulting.sim.engagement.domain.EngagementState;
import com.ibm.consulting.sim.identity.domain.User;
import com.ibm.consulting.sim.identity.domain.UserRole;
import com.ibm.consulting.sim.lead.domain.ResearchEvidenceRepository;
import com.ibm.consulting.sim.lead.domain.Lead;
import com.ibm.consulting.sim.lead.domain.LeadDifficulty;
import com.ibm.consulting.sim.meeting.domain.ConversationTurnRepository;
import com.ibm.consulting.sim.meeting.domain.MeetingRepository;
import com.ibm.consulting.sim.meeting.domain.PersonaStateRepository;
import com.ibm.consulting.sim.proposal.domain.Proposal;
import com.ibm.consulting.sim.proposal.domain.ProposalDraftContent;
import com.ibm.consulting.sim.proposal.domain.ProposalRepository;
import com.ibm.consulting.sim.proposal.domain.ProposalStatus;
import com.ibm.consulting.sim.scenario.application.DifficultyProfileService;
import com.ibm.consulting.sim.scenario.application.PersonaCatalogService;
import com.ibm.consulting.sim.scenario.application.PersonaProfile;
import com.ibm.consulting.sim.scenario.domain.DifficultyProfile;
import com.ibm.consulting.sim.scenario.domain.Persona;
import com.ibm.consulting.sim.scenario.domain.Scenario;
import com.ibm.consulting.sim.shared.config.CacheConfig;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import org.springframework.context.ApplicationEventPublisher;
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

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers(disabledWithoutDocker = true)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class ProposalConcurrencyIntegrationTest {

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
    @Autowired PlatformTransactionManager transactionManager;

    @Test
    void concurrentFirstDraftsCreateOneDraft() throws Exception {
        TestData data = inTransaction(this::persistDiscoveryCompleteEngagement);
        ProposalService service = service();

        List<Outcome> outcomes = runConcurrently(
                () -> service.saveDraft(data.engagementId(), data.userId(), draft("First")),
                () -> service.saveDraft(data.engagementId(), data.userId(), draft("Second")));

        Proposal persisted = findProposal(data.engagementId());
        assertThat(outcomes).allMatch(Outcome::succeeded);
        assertThat(proposalCount(data.engagementId())).isEqualTo(1L);
        assertThat(persisted.getStatus()).isEqualTo(ProposalStatus.DRAFT);
    }

    @Test
    void concurrentSubmissionsCreateOneDecisionAndOneDeterministicConflict() throws Exception {
        TestData data = inTransaction(this::persistDiscoveryCompleteEngagement);
        ProposalService service = service();

        List<Outcome> outcomes = runConcurrently(
                () -> service.submit(data.engagementId(), data.userId(), draft("First"), false),
                () -> service.submit(data.engagementId(), data.userId(), draft("Second"), false));

        Proposal persisted = findProposal(data.engagementId());
        Engagement engagement = findEngagement(data.engagementId());
        assertThat(proposalCount(data.engagementId())).isEqualTo(1L);
        assertThat(persisted.getStatus()).isEqualTo(ProposalStatus.SUBMITTED);
        assertThat(engagement.getState()).isEqualTo(EngagementState.CLIENT_DECISION);
        assertThat(outcomes).filteredOn(Outcome::succeeded).hasSize(1);
        assertThat(outcomes).filteredOn(outcome -> !outcome.succeeded())
                .extracting(Outcome::failure)
                .singleElement()
                .isInstanceOf(ProposalService.InvalidProposalStateException.class);
    }

    @Test
    void concurrentFirstDraftAndSubmissionNeverOverwriteSubmittedProposal() throws Exception {
        TestData data = inTransaction(this::persistDiscoveryCompleteEngagement);
        ProposalService service = service();

        List<Outcome> outcomes = runConcurrently(
                () -> service.saveDraft(data.engagementId(), data.userId(), draft("Draft")),
                () -> service.submit(data.engagementId(), data.userId(), draft("Submitted"), false));

        assertThat(proposalCount(data.engagementId())).isEqualTo(1L);
        assertThat(findProposal(data.engagementId()).getStatus()).isEqualTo(ProposalStatus.SUBMITTED);
        assertThat(findEngagement(data.engagementId()).getState()).isEqualTo(EngagementState.CLIENT_DECISION);
        assertThat(outcomes).filteredOn(Outcome::succeeded).hasSizeBetween(1, 2);
    }

    private ProposalService service() {
        DifficultyProfileService difficulty = mock(DifficultyProfileService.class);
        when(difficulty.forEngagement(any(Engagement.class))).thenReturn(DifficultyProfile.defaults(3, 3, 3, 3));
        PersonaCatalogService personas = mock(PersonaCatalogService.class);
        when(personas.getPersona(any())).thenReturn(new PersonaProfile());
        return new ProposalService(new EntityManagerProposalRepository(), new EntityManagerEngagementRepository(),
                mock(ResearchEvidenceRepository.class), mock(PersonaStateRepository.class), mock(MeetingRepository.class),
                mock(ConversationTurnRepository.class), mock(AiOrchestrationService.class), new ObjectMapper(), personas,
                difficulty, new ConcurrentMapCacheManager(CacheConfig.PROPOSAL_REVIEW_CACHE),
                mock(ApplicationEventPublisher.class));
    }

    private TestData persistDiscoveryCompleteEngagement() {
        User user = User.create(UUID.randomUUID() + "@example.com", "hash", "Learner", UserRole.LEARNER);
        Scenario scenario = Scenario.create("Proposal", "Technology", "Description", 3);
        Persona persona = Persona.create(scenario, "Client", "CIO", "Example", "Direct", null, null, null);
        Lead lead = Lead.create(scenario.getId(), "Example", "Technology", "Modernisation", LeadDifficulty.MEDIUM);
        entityManager.persist(user);
        entityManager.persist(scenario);
        entityManager.persist(persona);
        entityManager.persist(lead);
        Engagement engagement = Engagement.start(user.getId(), scenario.getId(), persona.getId());
        engagement.selectLead(lead.getId());
        engagement.transitionTo(EngagementState.HYPOTHESIS_READY, "ready");
        engagement.transitionTo(EngagementState.OUTREACHING, "outreach");
        engagement.transitionTo(EngagementState.MEETING_SECURED, "meeting");
        engagement.transitionTo(EngagementState.PREPARING, "preparing");
        engagement.transitionTo(EngagementState.IN_MEETING, "meeting started");
        engagement.transitionTo(EngagementState.DISCOVERY_COMPLETE, "discovery");
        entityManager.persist(engagement);
        entityManager.flush();
        return new TestData(user.getId(), engagement.getId());
    }

    private ProposalDraftContent draft(String marker) {
        return new ProposalDraftContent(marker + " grounded problem", "Focused pilot", List.of("Integration"),
                BigDecimal.valueOf(100_000), 8, "UNCONFIRMED", "Estimate", List.of(), List.of(), List.of(),
                List.of(), List.of());
    }

    private List<Outcome> runConcurrently(Callable<ProposalResponse> first,
                                          Callable<ProposalResponse> second) throws Exception {
        CountDownLatch start = new CountDownLatch(1);
        try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
            Future<Outcome> firstResult = executor.submit(() -> execute(start, first));
            Future<Outcome> secondResult = executor.submit(() -> execute(start, second));
            start.countDown();
            return List.of(firstResult.get(15, TimeUnit.SECONDS), secondResult.get(15, TimeUnit.SECONDS));
        }
    }

    private Outcome execute(CountDownLatch start, Callable<ProposalResponse> command) throws Exception {
        assertThat(start.await(5, TimeUnit.SECONDS)).isTrue();
        try {
            return new Outcome(inTransaction(command), null);
        } catch (RuntimeException failure) {
            return new Outcome(null, failure);
        }
    }

    private Proposal findProposal(UUID engagementId) {
        return inTransaction(() -> new EntityManagerProposalRepository().findByEngagementId(engagementId).orElseThrow());
    }

    private Engagement findEngagement(UUID engagementId) {
        return inTransaction(() -> entityManager.find(Engagement.class, engagementId));
    }

    private Long proposalCount(UUID engagementId) {
        return inTransaction(() -> entityManager.createQuery(
                        "select count(proposal) from Proposal proposal where proposal.engagementId = :id", Long.class)
                .setParameter("id", engagementId).getSingleResult());
    }

    private <T> T inTransaction(Callable<T> work) {
        return new TransactionTemplate(transactionManager).execute(status -> {
            try { return work.call(); }
            catch (RuntimeException exception) { throw exception; }
            catch (Exception exception) { throw new IllegalStateException(exception); }
        });
    }

    private final class EntityManagerEngagementRepository implements EngagementRepository {
        @Override public Engagement save(Engagement engagement) { return engagement; }
        @Override public List<Engagement> findAll() { return List.of(); }
        @Override public Optional<Engagement> findById(UUID id) {
            return Optional.ofNullable(entityManager.find(Engagement.class, id));
        }
        @Override public List<Engagement> findByUserId(UUID userId) { return List.of(); }
        @Override public List<Engagement> findDashboardByUserId(UUID userId) { return List.of(); }
        @Override public Optional<Engagement> findByIdAndUserId(UUID id, UUID userId) {
            return findById(id).filter(engagement -> userId.equals(engagement.getUserId()));
        }
        @Override public Optional<Engagement> findByIdAndUserIdForUpdate(UUID id, UUID userId) {
            Engagement engagement = entityManager.find(Engagement.class, id, LockModeType.PESSIMISTIC_WRITE);
            return Optional.ofNullable(engagement).filter(candidate -> userId.equals(candidate.getUserId()));
        }
    }

    private final class EntityManagerProposalRepository implements ProposalRepository {
        @Override public Proposal save(Proposal proposal) { return entityManager.merge(proposal); }
        @Override public Optional<Proposal> findByEngagementId(UUID engagementId) {
            return entityManager.createQuery(
                            "select proposal from Proposal proposal where proposal.engagementId = :id", Proposal.class)
                    .setParameter("id", engagementId).getResultStream().findFirst();
        }
    }

    private record TestData(UUID userId, UUID engagementId) {}
    private record Outcome(ProposalResponse response, RuntimeException failure) {
        boolean succeeded() { return response != null; }
    }
}

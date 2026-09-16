package com.ibm.consulting.sim.meeting.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ibm.consulting.sim.ai.application.AiOrchestrationService;
import com.ibm.consulting.sim.ai.domain.PersonaTurnResponse;
import com.ibm.consulting.sim.engagement.domain.Engagement;
import com.ibm.consulting.sim.engagement.domain.EngagementRepository;
import com.ibm.consulting.sim.engagement.domain.EngagementState;
import com.ibm.consulting.sim.identity.domain.User;
import com.ibm.consulting.sim.identity.domain.UserRole;
import com.ibm.consulting.sim.knowledge.application.KnowledgeRetrievalService;
import com.ibm.consulting.sim.lead.domain.Lead;
import com.ibm.consulting.sim.lead.domain.LeadDifficulty;
import com.ibm.consulting.sim.lead.domain.ResearchEvidenceRepository;
import com.ibm.consulting.sim.meeting.domain.ConversationActor;
import com.ibm.consulting.sim.meeting.domain.ConversationTurn;
import com.ibm.consulting.sim.meeting.domain.ConversationTurnRepository;
import com.ibm.consulting.sim.meeting.domain.Meeting;
import com.ibm.consulting.sim.meeting.domain.MeetingPreparationRepository;
import com.ibm.consulting.sim.meeting.domain.MeetingRepository;
import com.ibm.consulting.sim.meeting.domain.MeetingStatus;
import com.ibm.consulting.sim.meeting.domain.PersonaState;
import com.ibm.consulting.sim.meeting.domain.PersonaStateRepository;
import com.ibm.consulting.sim.scenario.application.DifficultyProfileService;
import com.ibm.consulting.sim.scenario.application.PersonaCatalogService;
import com.ibm.consulting.sim.scenario.application.PersonaProfile;
import com.ibm.consulting.sim.scenario.domain.DifficultyProfile;
import com.ibm.consulting.sim.scenario.domain.Persona;
import com.ibm.consulting.sim.scenario.domain.Scenario;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
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

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.Callable;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers(disabledWithoutDocker = true)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class MeetingExchangeTransactionIntegrationTest {

    private static final String MESSAGE_ID = "recoverable-message";

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

    private final MeetingRepository meetingRepository = new EntityManagerMeetingRepository();
    private final ConversationTurnRepository turnRepository = new EntityManagerTurnRepository();
    private final PersonaStateRepository personaStateRepository = new EntityManagerPersonaStateRepository();

    @Test
    void providerFailureRollsBackTheExchangeAndRetryPersistsExactlyOneTurnPair() {
        TestData data = inTransaction(this::persistInProgressMeeting);
        AiOrchestrationService ai = mock(AiOrchestrationService.class);
        doThrow(new IllegalStateException("provider unavailable"))
                .doReturn(PersonaTurnResponse.safeFallback("I can continue safely now."))
                .when(ai).execute(eq("persona_dialogue"), eq(data.engagementId()), anyString(), anyInt(), any(), any());
        MeetingService service = service(data, ai);

        assertThatThrownBy(() -> inTransaction(() -> service.sendMessage(
                data.meetingId(), data.userId(), "What are your priorities?", MESSAGE_ID)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("provider unavailable");

        assertThat(inTransaction(() -> turnRepository.countByMeetingId(data.meetingId()))).isZero();
        PersonaState stateAfterFailure = inTransaction(() -> personaStateRepository
                .findByEngagementId(data.engagementId()).orElseThrow());
        Meeting meetingAfterFailure = inTransaction(() -> meetingRepository.findById(data.meetingId()).orElseThrow());
        Engagement engagementAfterFailure = inTransaction(() -> entityManager.find(Engagement.class, data.engagementId()));
        assertThat(stateAfterFailure.getTrust()).isEqualTo(50);
        assertThat(stateAfterFailure.getInterest()).isEqualTo(50);
        assertThat(stateAfterFailure.getPatience()).isEqualTo(50);
        assertThat(meetingAfterFailure.getStatus()).isEqualTo(MeetingStatus.IN_PROGRESS);
        assertThat(engagementAfterFailure.getState()).isEqualTo(EngagementState.IN_MEETING);

        inTransaction(() -> service.sendMessage(
                data.meetingId(), data.userId(), "What are your priorities?", MESSAGE_ID));
        inTransaction(() -> service.sendMessage(
                data.meetingId(), data.userId(), "What are your priorities?", MESSAGE_ID));

        List<ConversationTurn> persisted = inTransaction(
                () -> turnRepository.findByMeetingIdOrderBySequenceAsc(data.meetingId()));
        assertThat(persisted).hasSize(2);
        assertThat(persisted).filteredOn(turn -> turn.getActor() == ConversationActor.LEARNER).hasSize(1);
        assertThat(persisted).filteredOn(turn -> turn.getActor() == ConversationActor.PERSONA).hasSize(1);
        assertThat(inTransaction(() -> meetingRepository.findById(data.meetingId()).orElseThrow()).getStatus())
                .isEqualTo(MeetingStatus.IN_PROGRESS);
    }

    private MeetingService service(TestData data, AiOrchestrationService ai) {
        DifficultyProfileService difficulty = mock(DifficultyProfileService.class);
        when(difficulty.forEngagement(any(Engagement.class))).thenReturn(data.profile());
        PersonaCatalogService personas = mock(PersonaCatalogService.class);
        when(personas.getPersona(data.persona().getId())).thenReturn(PersonaProfile.from(data.persona()));
        ResearchEvidenceRepository evidence = mock(ResearchEvidenceRepository.class);
        when(evidence.findByEngagementId(data.engagementId())).thenReturn(List.of());
        KnowledgeRetrievalService knowledge = mock(KnowledgeRetrievalService.class);
        when(knowledge.retrieveRelevantPassages(any(), any(), any(), anyString())).thenReturn(List.of());

        return new MeetingService(meetingRepository, turnRepository, personaStateRepository,
                mock(MeetingPreparationRepository.class), new EntityManagerEngagementRepository(), personas,
                evidence, ai, new ObjectMapper(), mock(TranscriptExportService.class), knowledge, difficulty,
                mock(GuidedMeetingResponseService.class));
    }

    private TestData persistInProgressMeeting() {
        User user = User.create("exchange-user@example.com", "hash", "Exchange User", UserRole.LEARNER);
        Scenario scenario = Scenario.create("Meeting exchange", "Technology", "Scenario", 3);
        Persona persona = Persona.create(scenario, "Client", "CIO", "Example Co", "Direct", "Risk",
                "Budget", "Delivery");
        Lead lead = Lead.create(scenario.getId(), "Example Co", "Technology", "Modernisation", LeadDifficulty.MEDIUM);
        entityManager.persist(user);
        entityManager.persist(scenario);
        entityManager.persist(persona);
        entityManager.persist(lead);
        Engagement engagement = Engagement.start(user.getId(), scenario.getId(), persona.getId());
        engagement.selectLead(lead.getId());
        engagement.transitionTo(EngagementState.HYPOTHESIS_READY, "Hypothesis ready");
        engagement.transitionTo(EngagementState.OUTREACHING, "Outreach started");
        engagement.transitionTo(EngagementState.MEETING_SECURED, "Meeting secured");
        engagement.transitionTo(EngagementState.PREPARING, "Preparation complete");
        engagement.transitionTo(EngagementState.IN_MEETING, "Meeting started");
        entityManager.persist(engagement);
        Meeting meeting = Meeting.start(engagement.getId(), persona.getId());
        entityManager.persist(meeting);
        DifficultyProfile profile = DifficultyProfile.defaults(3, 3, 3, 3);
        entityManager.persist(PersonaState.initial(engagement.getId(), profile));
        entityManager.flush();
        return new TestData(user.getId(), engagement.getId(), meeting.getId(), persona, profile);
    }

    private <T> T inTransaction(Callable<T> work) {
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
            return findByIdAndUserId(id, userId);
        }
    }

    private final class EntityManagerMeetingRepository implements MeetingRepository {
        @Override public Meeting save(Meeting meeting) {
            return entityManager.contains(meeting) ? meeting : entityManager.merge(meeting);
        }
        @Override public Optional<Meeting> findById(UUID id) {
            return Optional.ofNullable(entityManager.find(Meeting.class, id));
        }
        @Override public Optional<Meeting> findByIdForUpdate(UUID id) {
            return Optional.ofNullable(entityManager.find(Meeting.class, id, LockModeType.PESSIMISTIC_WRITE));
        }
        @Override public List<Meeting> findAllByEngagementIdOrderByCreatedAtAsc(UUID engagementId) {
            return entityManager.createQuery("""
                            select meeting from Meeting meeting
                            where meeting.engagementId = :engagementId
                            order by meeting.createdAt
                            """, Meeting.class)
                    .setParameter("engagementId", engagementId)
                    .getResultList();
        }
        @Override public List<Meeting> findAllByEngagementIdIn(List<UUID> engagementIds) {
            return entityManager.createQuery("""
                            select meeting from Meeting meeting
                            where meeting.engagementId in :engagementIds
                            """, Meeting.class)
                    .setParameter("engagementIds", engagementIds)
                    .getResultList();
        }
        @Override public Optional<Meeting> findByEngagementId(UUID engagementId) {
            return findAllByEngagementIdOrderByCreatedAtAsc(engagementId).stream().reduce((first, second) -> second);
        }
    }

    private final class EntityManagerTurnRepository implements ConversationTurnRepository {
        @Override public ConversationTurn save(ConversationTurn turn) {
            entityManager.persist(turn);
            return turn;
        }
        @Override public List<ConversationTurn> findByMeetingIdOrderBySequenceAsc(UUID meetingId) {
            return entityManager.createQuery("""
                            select turn from ConversationTurn turn
                            where turn.meetingId = :meetingId
                            order by turn.sequence
                            """, ConversationTurn.class)
                    .setParameter("meetingId", meetingId)
                    .getResultList();
        }
        @Override public int countByMeetingId(UUID meetingId) {
            return entityManager.createQuery("""
                            select count(turn) from ConversationTurn turn where turn.meetingId = :meetingId
                            """, Long.class)
                    .setParameter("meetingId", meetingId)
                    .getSingleResult().intValue();
        }
        @Override public Optional<ConversationTurn> findByMeetingIdAndClientMessageId(
                UUID meetingId, String clientMessageId) {
            return entityManager.createQuery("""
                            select turn from ConversationTurn turn
                            where turn.meetingId = :meetingId and turn.clientMessageId = :clientMessageId
                            """, ConversationTurn.class)
                    .setParameter("meetingId", meetingId)
                    .setParameter("clientMessageId", clientMessageId)
                    .getResultStream().findFirst();
        }
    }

    private final class EntityManagerPersonaStateRepository implements PersonaStateRepository {
        @Override public PersonaState save(PersonaState state) {
            return entityManager.contains(state) ? state : entityManager.merge(state);
        }
        @Override public Optional<PersonaState> findByEngagementId(UUID engagementId) {
            return entityManager.createQuery("""
                            select state from PersonaState state where state.engagementId = :engagementId
                            """, PersonaState.class)
                    .setParameter("engagementId", engagementId)
                    .getResultStream().findFirst();
        }
    }

    private record TestData(UUID userId, UUID engagementId, UUID meetingId, Persona persona,
                            DifficultyProfile profile) {}
}

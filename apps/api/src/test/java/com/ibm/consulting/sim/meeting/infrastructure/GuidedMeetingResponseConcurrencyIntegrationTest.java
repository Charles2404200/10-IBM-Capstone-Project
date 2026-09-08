package com.ibm.consulting.sim.meeting.infrastructure;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ibm.consulting.sim.ai.application.AiOrchestrationService;
import com.ibm.consulting.sim.engagement.domain.Engagement;
import com.ibm.consulting.sim.engagement.domain.EngagementRepository;
import com.ibm.consulting.sim.identity.domain.User;
import com.ibm.consulting.sim.identity.domain.UserRole;
import com.ibm.consulting.sim.knowledge.application.KnowledgeRetrievalService;
import com.ibm.consulting.sim.lead.domain.ResearchEvidenceRepository;
import com.ibm.consulting.sim.meeting.application.GuidedMeetingResponseService;
import com.ibm.consulting.sim.meeting.application.GuidedResponseOptions;
import com.ibm.consulting.sim.meeting.application.MeetingResponseOptionsResponse;
import com.ibm.consulting.sim.meeting.domain.ConversationTurnRepository;
import com.ibm.consulting.sim.meeting.domain.Meeting;
import com.ibm.consulting.sim.meeting.domain.MeetingRepository;
import com.ibm.consulting.sim.meeting.domain.MeetingResponseOptionSetRepository;
import com.ibm.consulting.sim.meeting.domain.PersonaStateRepository;
import com.ibm.consulting.sim.scenario.application.DifficultyProfileService;
import com.ibm.consulting.sim.scenario.application.PersonaCatalogService;
import com.ibm.consulting.sim.scenario.application.PersonaProfile;
import com.ibm.consulting.sim.scenario.domain.DifficultyProfile;
import com.ibm.consulting.sim.scenario.domain.Persona;
import com.ibm.consulting.sim.scenario.domain.Scenario;
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

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DataJpaTest
@Import({JpaMeetingRepository.class, JpaMeetingResponseOptionSetRepository.class})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers(disabledWithoutDocker = true)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class GuidedMeetingResponseConcurrencyIntegrationTest {

    private static final List<String> OPTIONS = List.of(
            "Could you quantify the operational impact of the current process?",
            "Which stakeholder owns the decision and the success measures?",
            "What constraints should shape a practical next step from here?");
    private static final DifficultyProfile PROFILE = DifficultyProfile.defaults(3, 3, 3, 3);

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
    @Autowired MeetingRepository meetings;
    @Autowired MeetingResponseOptionSetRepository optionSets;
    @Autowired PlatformTransactionManager transactionManager;

    @Test
    void concurrentOnDemandRequestsReturnOneDurableOptionSet() throws Exception {
        UUID meetingId = inTransaction(this::persistMeeting);
        Fixture fixture = fixture();

        List<MeetingResponseOptionsResponse> responses = runConcurrently(
                () -> fixture.service().optionsFor(meetingId, fixture.userId()),
                () -> fixture.service().optionsFor(meetingId, fixture.userId()));

        assertThat(responses).allMatch(MeetingResponseOptionsResponse::available);
        assertThat(countOptionSets(meetingId, 0)).isEqualTo(1L);
        verify(fixture.ai(), times(1)).execute(any(), any(), any(), any(Integer.class), any(), any());
    }

    @Test
    void onDemandAndPreGeneratedCreationRaceIsIdempotent() throws Exception {
        UUID meetingId = inTransaction(this::persistMeeting);
        Fixture fixture = fixture();

        List<MeetingResponseOptionsResponse> responses = runConcurrently(
                () -> fixture.service().optionsFor(meetingId, fixture.userId()),
                () -> fixture.service().cachePreGenerated(meetingId, 0, PROFILE, OPTIONS));

        assertThat(responses).allMatch(MeetingResponseOptionsResponse::available);
        assertThat(countOptionSets(meetingId, 0)).isEqualTo(1L);
    }

    @Test
    void differentMeetingsGenerateWithoutAProcessWideLock() throws Exception {
        UUID firstMeeting = inTransaction(this::persistMeeting);
        UUID secondMeeting = inTransaction(this::persistMeeting);
        Fixture fixture = fixture();
        CountDownLatch aiCallsReached = new CountDownLatch(2);
        when(fixture.ai().execute(any(), any(), any(), any(Integer.class), any(), any()))
                .thenAnswer(ignored -> {
                    aiCallsReached.countDown();
                    assertThat(aiCallsReached.await(5, TimeUnit.SECONDS)).isTrue();
                    return new GuidedResponseOptions(OPTIONS);
                });

        List<MeetingResponseOptionsResponse> responses = runConcurrently(
                () -> fixture.service().optionsFor(firstMeeting, fixture.userId()),
                () -> fixture.service().optionsFor(secondMeeting, fixture.userId()));

        assertThat(responses).allMatch(MeetingResponseOptionsResponse::available);
        assertThat(countOptionSets(firstMeeting, 0)).isEqualTo(1L);
        assertThat(countOptionSets(secondMeeting, 0)).isEqualTo(1L);
    }

    private Fixture fixture() {
        UUID userId = UUID.randomUUID();
        Engagement engagement = mock(Engagement.class);
        when(engagement.getId()).thenReturn(UUID.randomUUID());
        when(engagement.getScenarioId()).thenReturn(UUID.randomUUID());
        EngagementRepository engagements = mock(EngagementRepository.class);
        when(engagements.findByIdAndUserId(any(), any())).thenReturn(Optional.of(engagement));
        ConversationTurnRepository turns = mock(ConversationTurnRepository.class);
        when(turns.findByMeetingIdOrderBySequenceAsc(any())).thenReturn(List.of());
        PersonaStateRepository states = mock(PersonaStateRepository.class);
        when(states.findByEngagementId(any())).thenReturn(Optional.empty());
        ResearchEvidenceRepository evidence = mock(ResearchEvidenceRepository.class);
        when(evidence.findByEngagementId(any())).thenReturn(List.of());
        KnowledgeRetrievalService knowledge = mock(KnowledgeRetrievalService.class);
        when(knowledge.retrieveRelevantPassages(any(), any(), any(), any())).thenReturn(List.of());
        DifficultyProfileService difficulty = mock(DifficultyProfileService.class);
        when(difficulty.forEngagement(any())).thenReturn(PROFILE);
        PersonaCatalogService personas = mock(PersonaCatalogService.class);
        when(personas.getPersona(any())).thenReturn(mock(PersonaProfile.class));
        AiOrchestrationService ai = mock(AiOrchestrationService.class);
        when(ai.execute(any(), any(), any(), any(Integer.class), any(), any()))
                .thenReturn(new GuidedResponseOptions(OPTIONS));
        GuidedMeetingResponseService service = new GuidedMeetingResponseService(
                meetings, engagements, turns, states, optionSets, personas, evidence,
                knowledge, difficulty, ai, new ObjectMapper());
        return new Fixture(userId, service, ai);
    }

    private UUID persistMeeting() {
        User user = User.create(UUID.randomUUID() + "@example.com", "hash", "Learner", UserRole.LEARNER);
        Scenario scenario = Scenario.create("Guided responses", "Technology", "Scenario", 3);
        Persona persona = Persona.create(scenario, "Client", "CIO", "Example Co", "Direct",
                "Risk", "Budget", "Delivery");
        entityManager.persist(user);
        entityManager.persist(scenario);
        entityManager.persist(persona);
        Engagement engagement = Engagement.start(user.getId(), scenario.getId(), persona.getId());
        entityManager.persist(engagement);
        Meeting meeting = Meeting.start(engagement.getId(), persona.getId());
        entityManager.persist(meeting);
        entityManager.flush();
        return meeting.getId();
    }

    private long countOptionSets(UUID meetingId, int sourceSequence) {
        return inTransaction(() -> entityManager.createQuery("""
                        select count(optionSet) from MeetingResponseOptionSet optionSet
                        where optionSet.meetingId = :meetingId and optionSet.sourceSequence = :sourceSequence
                        """, Long.class)
                .setParameter("meetingId", meetingId)
                .setParameter("sourceSequence", sourceSequence)
                .getSingleResult());
    }

    private <T> List<T> runConcurrently(java.util.concurrent.Callable<T> first,
                                         java.util.concurrent.Callable<T> second) throws Exception {
        CountDownLatch start = new CountDownLatch(1);
        try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
            Future<T> firstResult = executor.submit(() -> execute(start, first));
            Future<T> secondResult = executor.submit(() -> execute(start, second));
            start.countDown();
            return List.of(firstResult.get(15, TimeUnit.SECONDS), secondResult.get(15, TimeUnit.SECONDS));
        }
    }

    private <T> T execute(CountDownLatch start, java.util.concurrent.Callable<T> command) throws Exception {
        assertThat(start.await(5, TimeUnit.SECONDS)).isTrue();
        return inTransaction(command);
    }

    private <T> T inTransaction(java.util.concurrent.Callable<T> work) {
        return new TransactionTemplate(transactionManager).execute(ignored -> {
            try { return work.call(); }
            catch (RuntimeException exception) { throw exception; }
            catch (Exception exception) { throw new IllegalStateException(exception); }
        });
    }

    private record Fixture(UUID userId, GuidedMeetingResponseService service, AiOrchestrationService ai) {}
}

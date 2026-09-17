package com.ibm.consulting.sim.meeting.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ibm.consulting.sim.ai.application.AiOrchestrationService;
import com.ibm.consulting.sim.ai.domain.PersonaTurnResponse;
import com.ibm.consulting.sim.engagement.domain.Engagement;
import com.ibm.consulting.sim.engagement.domain.EngagementRepository;
import com.ibm.consulting.sim.engagement.domain.EngagementState;
import com.ibm.consulting.sim.knowledge.application.KnowledgeRetrievalService;
import com.ibm.consulting.sim.knowledge.domain.KnowledgeCollection;
import com.ibm.consulting.sim.lead.domain.ResearchEvidenceRepository;
import com.ibm.consulting.sim.meeting.domain.ConversationActor;
import com.ibm.consulting.sim.meeting.domain.ConversationTurn;
import com.ibm.consulting.sim.meeting.domain.ConversationTurnRepository;
import com.ibm.consulting.sim.meeting.domain.Meeting;
import com.ibm.consulting.sim.meeting.domain.MeetingPreparationRepository;
import com.ibm.consulting.sim.meeting.domain.MeetingRepository;
import com.ibm.consulting.sim.meeting.domain.PersonaState;
import com.ibm.consulting.sim.meeting.domain.PersonaStateRepository;
import com.ibm.consulting.sim.scenario.application.DifficultyProfileService;
import com.ibm.consulting.sim.scenario.application.PersonaCatalogService;
import com.ibm.consulting.sim.scenario.application.PersonaProfile;
import com.ibm.consulting.sim.scenario.domain.DifficultyProfile;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MeetingMessageReplayServiceTest {

    @Mock MeetingRepository meetingRepository;
    @Mock PersonaStateRepository personaStateRepository;
    @Mock MeetingPreparationRepository preparationRepository;
    @Mock EngagementRepository engagementRepository;
    @Mock PersonaCatalogService personaCatalogService;
    @Mock ResearchEvidenceRepository evidenceRepository;
    @Mock AiOrchestrationService aiOrchestrationService;
    @Mock TranscriptExportService transcriptExportService;
    @Mock KnowledgeRetrievalService knowledgeRetrievalService;
    @Mock DifficultyProfileService difficultyProfileService;
    @Mock GuidedMeetingResponseService guidedResponseService;

    private final InMemoryTurnRepository turnRepository = new InMemoryTurnRepository();
    private MeetingService service;
    private TestData data;

    @BeforeEach
    void setUp() {
        data = inProgressMeeting();
        service = new MeetingService(meetingRepository, turnRepository, personaStateRepository,
                preparationRepository, engagementRepository, personaCatalogService, evidenceRepository,
                aiOrchestrationService, new ObjectMapper(), transcriptExportService,
                knowledgeRetrievalService, difficultyProfileService, guidedResponseService);

        when(meetingRepository.findByIdForUpdate(data.meeting().getId()))
                .thenReturn(Optional.of(data.meeting()));
        when(engagementRepository.findByIdAndUserId(data.engagement().getId(), data.userId()))
                .thenReturn(Optional.of(data.engagement()));
        when(difficultyProfileService.forEngagement(data.engagement())).thenReturn(data.profile());
        when(personaStateRepository.findByEngagementId(data.engagement().getId()))
                .thenReturn(Optional.of(data.personaState()));
        when(personaStateRepository.save(any(PersonaState.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(personaCatalogService.getPersona(data.meeting().getPersonaId())).thenReturn(data.persona());
        when(evidenceRepository.findByEngagementId(data.engagement().getId())).thenReturn(List.of());
        when(knowledgeRetrievalService.retrieveRelevantPassages(
                eq(KnowledgeCollection.SCENARIO_TRUTH), eq(data.engagement().getScenarioId()),
                eq(data.meeting().getPersonaId()), anyString())).thenReturn(List.of());
        doReturn(PersonaTurnResponse.safeFallback("Let us discuss that."))
                .when(aiOrchestrationService).execute(eq("persona_dialogue"), eq(data.engagement().getId()),
                        anyString(), anyInt(), any(), any());
    }

    @Test
    void sameClientMessageIdReplaysOneTurnPairWithoutApplyingStateTwice() {
        MeetingTurnResult first = service.sendMessage(
                data.meeting().getId(), data.userId(), "What are your main concerns?", "msg-123");
        MeetingTurnResult replay = service.sendMessage(
                data.meeting().getId(), data.userId(), "What are your main concerns?", "msg-123");

        assertThat(turnRepository.turns).hasSize(2);
        assertThat(turnRepository.turns).filteredOn(turn -> turn.getActor() == ConversationActor.LEARNER).hasSize(1);
        assertThat(replay.learnerTurn().id()).isEqualTo(first.learnerTurn().id());
        assertThat(replay.personaTurn().id()).isEqualTo(first.personaTurn().id());
        verify(aiOrchestrationService, times(1)).execute(eq("persona_dialogue"),
                eq(data.engagement().getId()), anyString(), anyInt(), any(), any());
        verify(personaStateRepository, times(1)).save(data.personaState());
        assertThat(data.meeting().getBehaviourLedger()).hasSize(1);
    }

    @Test
    void sameTrimmedTextInsideDuplicateWindowReplaysEvenWithANewRequestId() {
        MeetingTurnResult first = service.sendMessage(
                data.meeting().getId(), data.userId(), "What are your main concerns?", "msg-123");
        MeetingTurnResult replay = service.sendMessage(
                data.meeting().getId(), data.userId(), "  What are your main concerns?  ", "msg-456");

        assertThat(turnRepository.turns).hasSize(2);
        assertThat(replay.learnerTurn().id()).isEqualTo(first.learnerTurn().id());
        assertThat(replay.personaTurn().id()).isEqualTo(first.personaTurn().id());
        verify(aiOrchestrationService, times(1)).execute(eq("persona_dialogue"),
                eq(data.engagement().getId()), anyString(), anyInt(), any(), any());
    }

    @Test
    void sameTextOutsideDuplicateWindowCreatesANewTurnPair() {
        service.sendMessage(data.meeting().getId(), data.userId(), "What are your main concerns?", "msg-123");
        ReflectionTestUtils.setField(turnRepository.turns.getFirst(), "createdAt", Instant.now().minusSeconds(21));

        service.sendMessage(data.meeting().getId(), data.userId(), "What are your main concerns?", "msg-456");

        assertThat(turnRepository.turns).hasSize(4);
        assertThat(turnRepository.turns).filteredOn(turn -> turn.getActor() == ConversationActor.LEARNER).hasSize(2);
        verify(aiOrchestrationService, times(2)).execute(eq("persona_dialogue"),
                eq(data.engagement().getId()), anyString(), anyInt(), any(), any());
        verify(personaStateRepository, times(2)).save(data.personaState());
        assertThat(data.meeting().getBehaviourLedger()).hasSize(2);
    }

    private TestData inProgressMeeting() {
        UUID userId = UUID.randomUUID();
        UUID personaId = UUID.randomUUID();
        Engagement engagement = Engagement.start(userId, UUID.randomUUID(), personaId);
        engagement.selectLead(UUID.randomUUID());
        engagement.transitionTo(EngagementState.HYPOTHESIS_READY, "Hypothesis ready");
        engagement.transitionTo(EngagementState.OUTREACHING, "Outreach started");
        engagement.transitionTo(EngagementState.MEETING_SECURED, "Meeting secured");
        engagement.transitionTo(EngagementState.PREPARING, "Preparation complete");
        engagement.transitionTo(EngagementState.IN_MEETING, "Meeting started");
        Meeting meeting = Meeting.start(engagement.getId(), personaId);
        DifficultyProfile profile = DifficultyProfile.defaults(3, 3, 3, 3);
        PersonaState state = PersonaState.initial(engagement.getId(), profile);
        PersonaProfile persona = new PersonaProfile();
        persona.setId(personaId);
        persona.setName("Client");
        persona.setJobTitle("CIO");
        persona.setOrganisation("Example Co");
        persona.setCommunicationStyle("Direct");
        persona.setVisibleConcerns("Delivery risk");
        persona.setHiddenConcerns("Budget");
        persona.setBusinessGoals("Modernisation");
        return new TestData(userId, engagement, meeting, state, profile, persona);
    }

    private static final class InMemoryTurnRepository implements ConversationTurnRepository {
        private final List<ConversationTurn> turns = new ArrayList<>();

        @Override
        public ConversationTurn save(ConversationTurn turn) {
            turns.add(turn);
            turns.sort(Comparator.comparingInt(ConversationTurn::getSequence));
            return turn;
        }

        @Override
        public List<ConversationTurn> findByMeetingIdOrderBySequenceAsc(UUID meetingId) {
            return turns.stream().filter(turn -> meetingId.equals(turn.getMeetingId())).toList();
        }

        @Override
        public int countByMeetingId(UUID meetingId) {
            return (int) turns.stream().filter(turn -> meetingId.equals(turn.getMeetingId())).count();
        }

        @Override
        public Optional<ConversationTurn> findByMeetingIdAndClientMessageId(UUID meetingId, String clientMessageId) {
            return turns.stream()
                    .filter(turn -> meetingId.equals(turn.getMeetingId()))
                    .filter(turn -> clientMessageId.equals(turn.getClientMessageId()))
                    .findFirst();
        }
    }

    private record TestData(UUID userId, Engagement engagement, Meeting meeting, PersonaState personaState,
                            DifficultyProfile profile, PersonaProfile persona) {}
}

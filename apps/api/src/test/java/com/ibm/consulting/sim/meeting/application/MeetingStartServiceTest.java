package com.ibm.consulting.sim.meeting.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ibm.consulting.sim.ai.application.AiOrchestrationService;
import com.ibm.consulting.sim.engagement.domain.Engagement;
import com.ibm.consulting.sim.engagement.domain.EngagementRepository;
import com.ibm.consulting.sim.engagement.domain.EngagementState;
import com.ibm.consulting.sim.knowledge.application.KnowledgeRetrievalService;
import com.ibm.consulting.sim.lead.domain.ResearchEvidenceRepository;
import com.ibm.consulting.sim.meeting.domain.ConversationTurnRepository;
import com.ibm.consulting.sim.meeting.domain.Meeting;
import com.ibm.consulting.sim.meeting.domain.MeetingPreparationRepository;
import com.ibm.consulting.sim.meeting.domain.MeetingRepository;
import com.ibm.consulting.sim.meeting.domain.MeetingStatus;
import com.ibm.consulting.sim.meeting.domain.PersonaState;
import com.ibm.consulting.sim.meeting.domain.PersonaStateRepository;
import com.ibm.consulting.sim.meeting.domain.PreparationNotReadyException;
import com.ibm.consulting.sim.scenario.application.DifficultyProfileService;
import com.ibm.consulting.sim.scenario.application.PersonaCatalogService;
import com.ibm.consulting.sim.scenario.domain.DifficultyProfile;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MeetingStartServiceTest {

    @Mock MeetingRepository meetingRepository;
    @Mock ConversationTurnRepository turnRepository;
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

    private MeetingService service;

    @BeforeEach
    void setUp() {
        service = new MeetingService(meetingRepository, turnRepository, personaStateRepository,
                preparationRepository, engagementRepository, personaCatalogService, evidenceRepository,
                aiOrchestrationService, new ObjectMapper(), transcriptExportService,
                knowledgeRetrievalService, difficultyProfileService, guidedResponseService);
    }

    @Test
    void validStartCreatesOneInProgressMeetingAndInitializesPersonaStateOnce() {
        TestData data = preparingEngagement();
        when(engagementRepository.findByIdAndUserIdForUpdate(data.engagement().getId(), data.userId()))
                .thenReturn(Optional.of(data.engagement()));
        when(difficultyProfileService.forEngagement(data.engagement())).thenReturn(data.profile());
        when(personaStateRepository.findByEngagementId(data.engagement().getId())).thenReturn(Optional.empty());
        when(personaStateRepository.save(any(PersonaState.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        MeetingResponse response = service.start(data.engagement().getId(), data.userId());

        ArgumentCaptor<Meeting> savedMeeting = ArgumentCaptor.forClass(Meeting.class);
        verify(meetingRepository).save(savedMeeting.capture());
        verify(personaStateRepository).save(any(PersonaState.class));
        verify(engagementRepository).save(data.engagement());
        assertThat(savedMeeting.getValue().getStatus()).isEqualTo(MeetingStatus.IN_PROGRESS);
        assertThat(response.id()).isEqualTo(savedMeeting.getValue().getId());
        assertThat(data.engagement().getState()).isEqualTo(EngagementState.IN_MEETING);
        assertThat(data.engagement().getEvents())
                .filteredOn(event -> event.getState() == EngagementState.IN_MEETING)
                .hasSize(1);
    }

    @Test
    void sequentialRepeatedStartRejectsWithoutDuplicatingMeetingPersonaStateOrLifecycleEvent() {
        TestData data = preparingEngagement();
        when(engagementRepository.findByIdAndUserIdForUpdate(data.engagement().getId(), data.userId()))
                .thenReturn(Optional.of(data.engagement()));
        when(difficultyProfileService.forEngagement(data.engagement())).thenReturn(data.profile());
        when(personaStateRepository.findByEngagementId(data.engagement().getId())).thenReturn(Optional.empty());
        when(personaStateRepository.save(any(PersonaState.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        service.start(data.engagement().getId(), data.userId());

        assertThatThrownBy(() -> service.start(data.engagement().getId(), data.userId()))
                .isInstanceOf(PreparationNotReadyException.class);
        verify(meetingRepository, times(1)).save(any(Meeting.class));
        verify(personaStateRepository, times(1)).save(any(PersonaState.class));
        verify(engagementRepository, times(1)).save(data.engagement());
        assertThat(data.engagement().getState()).isEqualTo(EngagementState.IN_MEETING);
        assertThat(data.engagement().getEvents())
                .filteredOn(event -> event.getState() == EngagementState.IN_MEETING)
                .hasSize(1);
    }

    private TestData preparingEngagement() {
        UUID userId = UUID.randomUUID();
        Engagement engagement = Engagement.start(userId, UUID.randomUUID(), UUID.randomUUID());
        engagement.selectLead(UUID.randomUUID());
        engagement.transitionTo(EngagementState.HYPOTHESIS_READY, "Hypothesis ready");
        engagement.transitionTo(EngagementState.OUTREACHING, "Outreach started");
        engagement.transitionTo(EngagementState.MEETING_SECURED, "Meeting secured");
        engagement.transitionTo(EngagementState.PREPARING, "Preparation complete");
        return new TestData(userId, engagement, DifficultyProfile.defaults(3, 3, 3, 3));
    }

    private record TestData(UUID userId, Engagement engagement, DifficultyProfile profile) {}
}

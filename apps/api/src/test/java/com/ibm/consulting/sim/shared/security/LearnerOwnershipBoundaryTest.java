package com.ibm.consulting.sim.shared.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ibm.consulting.sim.ai.application.AiOrchestrationService;
import com.ibm.consulting.sim.assessment.domain.AssessmentRepository;
import com.ibm.consulting.sim.engagement.application.EngagementQueryService;
import com.ibm.consulting.sim.engagement.domain.EngagementRepository;
import com.ibm.consulting.sim.lead.domain.LeadRepository;
import com.ibm.consulting.sim.lead.domain.ResearchEvidenceRepository;
import com.ibm.consulting.sim.meeting.application.MeetingPreparationService;
import com.ibm.consulting.sim.meeting.application.MeetingService;
import com.ibm.consulting.sim.meeting.application.MeetingServiceTestFactory;
import com.ibm.consulting.sim.meeting.domain.ConversationTurnRepository;
import com.ibm.consulting.sim.meeting.domain.Meeting;
import com.ibm.consulting.sim.meeting.domain.MeetingPreparationRepository;
import com.ibm.consulting.sim.meeting.domain.MeetingRepository;
import com.ibm.consulting.sim.meeting.domain.PersonaStateRepository;
import com.ibm.consulting.sim.outreach.application.OutreachService;
import com.ibm.consulting.sim.outreach.domain.OutreachRepository;
import com.ibm.consulting.sim.proposal.application.ProposalService;
import com.ibm.consulting.sim.proposal.domain.ProposalDraftContent;
import com.ibm.consulting.sim.proposal.domain.ProposalRepository;
import com.ibm.consulting.sim.scenario.application.DifficultyProfileService;
import com.ibm.consulting.sim.scenario.application.PersonaCatalogService;
import com.ibm.consulting.sim.scenario.domain.DifficultyProfile;
import com.ibm.consulting.sim.scenario.domain.Persona;
import com.ibm.consulting.sim.scenario.domain.Scenario;
import com.ibm.consulting.sim.scenario.domain.ScenarioRepository;
import com.ibm.consulting.sim.shared.config.CacheConfig;
import com.ibm.consulting.sim.shared.domain.NotFoundException;
import org.junit.jupiter.api.Test;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import org.springframework.context.ApplicationEventPublisher;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class LearnerOwnershipBoundaryTest {

    @Test
    void anotherUserCannotReadTheEngagementWorkspace() {
        EngagementRepository engagements = mock(EngagementRepository.class);
        AssessmentRepository assessments = mock(AssessmentRepository.class);
        ScenarioRepository scenarios = mock(ScenarioRepository.class);
        LeadRepository leads = mock(LeadRepository.class);
        ResearchEvidenceRepository evidence = mock(ResearchEvidenceRepository.class);
        MeetingRepository meetings = mock(MeetingRepository.class);
        EngagementQueryService service = new EngagementQueryService(
                engagements, assessments, scenarios, leads, evidence, meetings);
        UUID engagementId = UUID.randomUUID();
        UUID otherUserId = UUID.randomUUID();

        assertThatThrownBy(() -> service.getWorkspace(engagementId, otherUserId))
                .isInstanceOf(NotFoundException.class);

        verify(scenarios, never()).findById(any());
        verify(leads, never()).findById(any());
        verify(evidence, never()).countByEngagementId(any());
        verify(meetings, never()).findByEngagementId(any());
    }

    @Test
    void anotherUserCannotReadOrUpdateMeetingPreparation() {
        MeetingPreparationRepository preparations = mock(MeetingPreparationRepository.class);
        EngagementRepository engagements = mock(EngagementRepository.class);
        MeetingPreparationService service = new MeetingPreparationService(preparations, engagements);
        UUID engagementId = UUID.randomUUID();
        UUID otherUserId = UUID.randomUUID();

        assertThatThrownBy(() -> service.get(engagementId, otherUserId))
                .isInstanceOf(NotFoundException.class);
        assertThatThrownBy(() -> service.update(
                engagementId, otherUserId, "Objective", List.of("Agenda"), List.of("Question")))
                .isInstanceOf(NotFoundException.class);

        verify(preparations, never()).findByEngagementId(any());
        verify(preparations, never()).save(any());
        verify(engagements, never()).save(any());
    }

    @Test
    void anotherUserCannotReadOrCreateOutreachAttempts() {
        OutreachRepository outreach = mock(OutreachRepository.class);
        EngagementRepository engagements = mock(EngagementRepository.class);
        AiOrchestrationService ai = mock(AiOrchestrationService.class);
        LeadRepository leads = mock(LeadRepository.class);
        ResearchEvidenceRepository evidence = mock(ResearchEvidenceRepository.class);
        OutreachService service = new OutreachService(outreach, engagements, ai, new ObjectMapper(),
                mock(DifficultyProfileService.class), leads, evidence);
        UUID engagementId = UUID.randomUUID();
        UUID otherUserId = UUID.randomUUID();

        assertThatThrownBy(() -> service.listAttempts(engagementId, otherUserId))
                .isInstanceOf(NotFoundException.class);
        assertThatThrownBy(() -> service.send(
                engagementId, otherUserId, "Private subject", "Private outreach body", "private-request"))
                .isInstanceOf(NotFoundException.class);

        verify(outreach, never()).findByEngagementId(any());
        verify(outreach, never()).save(any());
        verify(ai, never()).execute(any(), any(), any(), anyInt(), any(), any());
        verify(engagements, never()).save(any());
    }

    @Test
    void anotherUserCannotStartReadMessageCompleteOrRetryAMeeting() {
        MeetingRepository meetings = mock(MeetingRepository.class);
        ConversationTurnRepository turns = mock(ConversationTurnRepository.class);
        EngagementRepository engagements = mock(EngagementRepository.class);
        Scenario scenario = Scenario.create("Ownership", "Technology", "Scenario", 3);
        Persona persona = Persona.create(scenario, "Client", "CIO", "Example Co", "Direct",
                "Risk", "Budget", "Delivery");
        Meeting meeting = Meeting.start(UUID.randomUUID(), persona.getId());
        UUID otherUserId = UUID.randomUUID();
        when(meetings.findById(meeting.getId())).thenReturn(Optional.of(meeting));
        when(meetings.findByIdForUpdate(meeting.getId())).thenReturn(Optional.of(meeting));
        MeetingService service = MeetingServiceTestFactory.forImmediateTermination(
                meetings, turns, engagements, meeting.getEngagementId(), persona,
                DifficultyProfile.defaults(3, 3, 3, 3));

        assertThatThrownBy(() -> service.start(meeting.getEngagementId(), otherUserId))
                .isInstanceOf(NotFoundException.class);
        assertThatThrownBy(() -> service.get(meeting.getId(), otherUserId))
                .isInstanceOf(NotFoundException.class);
        assertThatThrownBy(() -> service.transcript(meeting.getId(), otherUserId))
                .isInstanceOf(NotFoundException.class);
        assertThatThrownBy(() -> service.personaState(meeting.getId(), otherUserId))
                .isInstanceOf(NotFoundException.class);
        assertThatThrownBy(() -> service.sendMessage(
                meeting.getId(), otherUserId, "Private learner message", "private-id"))
                .isInstanceOf(NotFoundException.class);
        assertThatThrownBy(() -> service.complete(meeting.getId(), otherUserId))
                .isInstanceOf(NotFoundException.class);
        assertThatThrownBy(() -> service.retry(meeting.getId(), otherUserId))
                .isInstanceOf(NotFoundException.class);

        verify(meetings, never()).save(any());
        verify(turns, never()).save(any());
        verify(engagements, never()).save(any());
    }

    @Test
    void anotherUserCannotAccessOrMutateAnyProposalWorkspaceOperation() {
        ProposalRepository proposals = mock(ProposalRepository.class);
        EngagementRepository engagements = mock(EngagementRepository.class);
        ResearchEvidenceRepository evidence = mock(ResearchEvidenceRepository.class);
        PersonaStateRepository personaStates = mock(PersonaStateRepository.class);
        MeetingRepository meetings = mock(MeetingRepository.class);
        ConversationTurnRepository turns = mock(ConversationTurnRepository.class);
        AiOrchestrationService ai = mock(AiOrchestrationService.class);
        ApplicationEventPublisher events = mock(ApplicationEventPublisher.class);
        ProposalService service = new ProposalService(proposals, engagements, evidence, personaStates, meetings,
                turns, ai, new ObjectMapper(), mock(PersonaCatalogService.class),
                mock(DifficultyProfileService.class),
                new ConcurrentMapCacheManager(CacheConfig.PROPOSAL_REVIEW_CACHE), events);
        UUID engagementId = UUID.randomUUID();
        UUID otherUserId = UUID.randomUUID();
        ProposalDraftContent draft = new ProposalDraftContent(
                "Private problem", "Private solution", List.of("Private component"),
                BigDecimal.TEN, 2, "MEDIUM", "Private source", List.of(), List.of(), List.of(),
                List.of(), List.of());

        assertThatThrownBy(() -> service.workspace(engagementId, otherUserId))
                .isInstanceOf(NotFoundException.class);
        assertThatThrownBy(() -> service.saveDraft(engagementId, otherUserId, draft))
                .isInstanceOf(NotFoundException.class);
        assertThatThrownBy(() -> service.review(engagementId, otherUserId, draft))
                .isInstanceOf(NotFoundException.class);
        assertThatThrownBy(() -> service.challenge(engagementId, otherUserId, draft))
                .isInstanceOf(NotFoundException.class);
        assertThatThrownBy(() -> service.submit(engagementId, otherUserId, draft, true))
                .isInstanceOf(NotFoundException.class);
        assertThatThrownBy(() -> service.get(engagementId, otherUserId))
                .isInstanceOf(NotFoundException.class);
        assertThatThrownBy(() -> service.explainDecision(engagementId, otherUserId))
                .isInstanceOf(NotFoundException.class);
        assertThatThrownBy(() -> service.counterfactual(engagementId, otherUserId))
                .isInstanceOf(NotFoundException.class);

        verify(proposals, never()).findByEngagementId(any());
        verify(proposals, never()).save(any());
        verify(evidence, never()).findByEngagementId(any());
        verify(ai, never()).execute(any(), any(), any(), anyInt(), any(), any());
        verify(events, never()).publishEvent(any(Object.class));
        verify(engagements, never()).save(any());
    }
}

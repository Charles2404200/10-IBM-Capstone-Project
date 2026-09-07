package com.ibm.consulting.sim.meeting.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ibm.consulting.sim.ai.application.AiOrchestrationService;
import com.ibm.consulting.sim.engagement.domain.Engagement;
import com.ibm.consulting.sim.engagement.domain.EngagementRepository;
import com.ibm.consulting.sim.knowledge.application.KnowledgeRetrievalService;
import com.ibm.consulting.sim.lead.domain.ResearchEvidenceRepository;
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
import com.ibm.consulting.sim.scenario.domain.Persona;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** Test-only assembly for the persistence-focused meeting concurrency test. */
public final class MeetingServiceTestFactory {

    private MeetingServiceTestFactory() {}

    public static MeetingService forImmediateTermination(
            MeetingRepository meetings,
            ConversationTurnRepository turns,
            EngagementRepository engagements,
            UUID engagementId,
            Persona persona,
            DifficultyProfile profile) {
        PersonaStateRepository personaStates = mock(PersonaStateRepository.class);
        when(personaStates.findByEngagementId(engagementId))
                .thenAnswer(ignored -> Optional.of(PersonaState.initial(engagementId, profile)));
        PersonaCatalogService personas = mock(PersonaCatalogService.class);
        when(personas.getPersona(persona.getId())).thenReturn(PersonaProfile.from(persona));
        ResearchEvidenceRepository evidence = mock(ResearchEvidenceRepository.class);
        when(evidence.findByEngagementId(engagementId)).thenReturn(List.of());
        DifficultyProfileService difficulty = mock(DifficultyProfileService.class);
        when(difficulty.forEngagement(any(Engagement.class))).thenReturn(profile);
        TranscriptExportService transcriptExporter = mock(TranscriptExportService.class);
        when(transcriptExporter.export(any(Meeting.class))).thenReturn(null);

        return new MeetingService(
                meetings,
                turns,
                personaStates,
                mock(MeetingPreparationRepository.class),
                engagements,
                personas,
                evidence,
                mock(AiOrchestrationService.class),
                new ObjectMapper(),
                transcriptExporter,
                mock(KnowledgeRetrievalService.class),
                difficulty,
                mock(GuidedMeetingResponseService.class));
    }
}

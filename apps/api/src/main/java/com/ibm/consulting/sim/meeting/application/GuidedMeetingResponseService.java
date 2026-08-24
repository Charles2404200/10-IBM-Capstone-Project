package com.ibm.consulting.sim.meeting.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ibm.consulting.sim.ai.application.AiOrchestrationService;
import com.ibm.consulting.sim.engagement.domain.Engagement;
import com.ibm.consulting.sim.engagement.domain.EngagementRepository;
import com.ibm.consulting.sim.knowledge.application.KnowledgeRetrievalService;
import com.ibm.consulting.sim.knowledge.domain.KnowledgeCollection;
import com.ibm.consulting.sim.lead.domain.ResearchEvidence;
import com.ibm.consulting.sim.lead.domain.ResearchEvidenceRepository;
import com.ibm.consulting.sim.meeting.domain.*;
import com.ibm.consulting.sim.scenario.application.DifficultyProfileService;
import com.ibm.consulting.sim.scenario.application.PersonaCatalogService;
import com.ibm.consulting.sim.scenario.application.PersonaProfile;
import com.ibm.consulting.sim.scenario.domain.DifficultyProfile;
import com.ibm.consulting.sim.shared.domain.NotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/** Generates and durably caches AI-guided responses for Easy and Medium meetings. */
@Service
public class GuidedMeetingResponseService {

    private static final int PROMPT_VERSION = 1;

    private final MeetingRepository meetingRepository;
    private final EngagementRepository engagementRepository;
    private final ConversationTurnRepository turnRepository;
    private final PersonaStateRepository personaStateRepository;
    private final MeetingResponseOptionSetRepository optionSetRepository;
    private final PersonaCatalogService personaCatalogService;
    private final ResearchEvidenceRepository evidenceRepository;
    private final KnowledgeRetrievalService knowledgeRetrievalService;
    private final DifficultyProfileService difficultyProfileService;
    private final AiOrchestrationService aiOrchestrationService;
    private final ObjectMapper objectMapper;

    public GuidedMeetingResponseService(MeetingRepository meetingRepository,
                                        EngagementRepository engagementRepository,
                                        ConversationTurnRepository turnRepository,
                                        PersonaStateRepository personaStateRepository,
                                        MeetingResponseOptionSetRepository optionSetRepository,
                                        PersonaCatalogService personaCatalogService,
                                        ResearchEvidenceRepository evidenceRepository,
                                        KnowledgeRetrievalService knowledgeRetrievalService,
                                        DifficultyProfileService difficultyProfileService,
                                        AiOrchestrationService aiOrchestrationService,
                                        ObjectMapper objectMapper) {
        this.meetingRepository = meetingRepository;
        this.engagementRepository = engagementRepository;
        this.turnRepository = turnRepository;
        this.personaStateRepository = personaStateRepository;
        this.optionSetRepository = optionSetRepository;
        this.personaCatalogService = personaCatalogService;
        this.evidenceRepository = evidenceRepository;
        this.knowledgeRetrievalService = knowledgeRetrievalService;
        this.difficultyProfileService = difficultyProfileService;
        this.aiOrchestrationService = aiOrchestrationService;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public MeetingResponseOptionsResponse optionsFor(UUID meetingId, UUID userId) {
        Meeting meeting = meetingRepository.findById(meetingId)
                .orElseThrow(() -> new NotFoundException("Meeting", meetingId));
        Engagement engagement = engagementRepository.findByIdAndUserId(meeting.getEngagementId(), userId)
                .orElseThrow(() -> new NotFoundException("Meeting", meetingId));
        DifficultyProfile profile = difficultyProfileService.forEngagement(engagement);
        if (MeetingInteractionMode.forDifficulty(profile.level()) == MeetingInteractionMode.FREEFORM) {
            return MeetingResponseOptionsResponse.freeform();
        }
        if (meeting.getStatus() != MeetingStatus.IN_PROGRESS) {
            return MeetingResponseOptionsResponse.unavailable(0, "This meeting is no longer accepting responses.");
        }

        List<ConversationTurn> turns = turnRepository.findByMeetingIdOrderBySequenceAsc(meetingId);
        int sourceSequence = turns.stream()
                .filter(turn -> turn.getActor() == ConversationActor.PERSONA)
                .mapToInt(ConversationTurn::getSequence)
                .max().orElse(0);
        return optionSetRepository.findByMeetingIdAndSourceSequence(meetingId, sourceSequence)
                .map(MeetingResponseOptionsResponse::from)
                .orElseGet(() -> generate(meeting, engagement, turns, sourceSequence, profile));
    }

    private MeetingResponseOptionsResponse generate(Meeting meeting, Engagement engagement,
                                                     List<ConversationTurn> turns, int sourceSequence,
                                                     DifficultyProfile profile) {
        PersonaProfile persona = personaCatalogService.getPersona(meeting.getPersonaId());
        PersonaState state = personaStateRepository.findByEngagementId(engagement.getId())
                .orElseGet(() -> PersonaState.initial(engagement.getId(), profile));
        List<ResearchEvidence> evidence = evidenceRepository.findByEngagementId(engagement.getId());
        String retrievalQuery = turns.stream().filter(turn -> turn.getActor() == ConversationActor.PERSONA)
                .reduce((ignored, latest) -> latest).map(ConversationTurn::getContent)
                .orElse("Open the meeting with a focused discovery question.");
        List<String> knowledge = knowledgeRetrievalService.retrieveRelevantPassages(
                KnowledgeCollection.SCENARIO_TRUTH, engagement.getScenarioId(), persona.getId(), retrievalQuery);

        GuidedResponseOptions generated = aiOrchestrationService.execute(
                "guided_meeting_options", engagement.getId(),
                GuidedResponsePromptAssembler.assemble(persona, state, evidence, knowledge, turns, profile), PROMPT_VERSION,
                new GuidedResponseOptionsParser(objectMapper), () -> new GuidedResponseOptions(List.of()));
        List<String> balancedOptions = GuidedResponseBalancePolicy.balance(generated.options(), profile, sourceSequence);
        if (!validOptions(balancedOptions)) {
            return MeetingResponseOptionsResponse.unavailable(sourceSequence,
                    "Guided responses are temporarily unavailable. Please try again.");
        }
        MeetingResponseOptionSet optionSet = optionSetRepository.save(
                MeetingResponseOptionSet.generated(meeting.getId(), sourceSequence, balancedOptions));
        return MeetingResponseOptionsResponse.from(optionSet);
    }

    /**
     * Stores choices generated alongside the persona reply so the WebSocket event
     * can deliver them immediately. Invalid model output is deliberately not kept;
     * the read endpoint can then perform a fresh, validated generation on retry.
     */
    public MeetingResponseOptionsResponse cachePreGenerated(UUID meetingId, int sourceSequence,
                                                             DifficultyProfile profile, List<String> options) {
        if (MeetingInteractionMode.forDifficulty(profile.level()) == MeetingInteractionMode.FREEFORM) {
            return MeetingResponseOptionsResponse.freeform();
        }
        List<String> balancedOptions = GuidedResponseBalancePolicy.balance(options, profile, sourceSequence);
        if (!validOptions(balancedOptions)) {
            return MeetingResponseOptionsResponse.unavailable(sourceSequence,
                    "Guided responses are temporarily unavailable. Please try again.");
        }
        return optionSetRepository.findByMeetingIdAndSourceSequence(meetingId, sourceSequence)
                .map(MeetingResponseOptionsResponse::from)
                .orElseGet(() -> MeetingResponseOptionsResponse.from(optionSetRepository.save(
                        MeetingResponseOptionSet.generated(meetingId, sourceSequence, balancedOptions))));
    }

    private boolean validOptions(List<String> options) {
        if (options == null || options.size() != 3) {
            return false;
        }
        return options.stream().allMatch(option -> option != null && option.trim().length() >= 20
                && option.trim().length() <= 900)
                && options.stream().map(option -> option.trim().toLowerCase(java.util.Locale.ROOT)).distinct().count() == 3;
    }
}

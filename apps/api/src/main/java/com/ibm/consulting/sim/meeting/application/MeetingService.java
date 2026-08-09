package com.ibm.consulting.sim.meeting.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ibm.consulting.sim.ai.application.AiOrchestrationService;
import com.ibm.consulting.sim.ai.domain.PersonaTurnResponse;
import com.ibm.consulting.sim.ai.infrastructure.PersonaTurnResponseParser;
import com.ibm.consulting.sim.engagement.domain.Engagement;
import com.ibm.consulting.sim.engagement.domain.EngagementRepository;
import com.ibm.consulting.sim.engagement.domain.EngagementState;
import com.ibm.consulting.sim.knowledge.application.KnowledgeRetrievalService;
import com.ibm.consulting.sim.knowledge.domain.KnowledgeCollection;
import com.ibm.consulting.sim.lead.domain.ResearchEvidence;
import com.ibm.consulting.sim.lead.domain.ResearchEvidenceRepository;
import com.ibm.consulting.sim.meeting.domain.*;
import com.ibm.consulting.sim.scenario.application.PersonaCatalogService;
import com.ibm.consulting.sim.scenario.application.DifficultyProfileService;
import com.ibm.consulting.sim.scenario.application.PersonaProfile;
import com.ibm.consulting.sim.scenario.domain.DifficultyProfile;
import com.ibm.consulting.sim.shared.domain.DomainException;
import com.ibm.consulting.sim.shared.domain.NotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Orchestrates the live meeting lifecycle: starting a meeting, exchanging
 * grounded persona turns, and completing the meeting. This is the sole
 * writer of {@link PersonaState} and the meeting transcript.
 */
@Service
public class MeetingService {

        private static final int PROMPT_TRANSCRIPT_WINDOW = 8;
    private static final int PROMPT_VERSION = 1;
    private static final java.time.Duration DUPLICATE_WINDOW = java.time.Duration.ofSeconds(20);

    private final MeetingRepository meetingRepository;
    private final ConversationTurnRepository turnRepository;
    private final PersonaStateRepository personaStateRepository;
    private final MeetingPreparationRepository preparationRepository;
    private final EngagementRepository engagementRepository;
    private final PersonaCatalogService personaCatalogService;
    private final ResearchEvidenceRepository evidenceRepository;
    private final AiOrchestrationService aiOrchestrationService;
    private final ObjectMapper objectMapper;
    private final TranscriptExportService transcriptExportService;
    private final KnowledgeRetrievalService knowledgeRetrievalService;
    private final DifficultyProfileService difficultyProfileService;

    public MeetingService(MeetingRepository meetingRepository,
                           ConversationTurnRepository turnRepository,
                           PersonaStateRepository personaStateRepository,
                           MeetingPreparationRepository preparationRepository,
                           EngagementRepository engagementRepository,
                           PersonaCatalogService personaCatalogService,
                           ResearchEvidenceRepository evidenceRepository,
                           AiOrchestrationService aiOrchestrationService,
                           ObjectMapper objectMapper,
                           TranscriptExportService transcriptExportService,
                           KnowledgeRetrievalService knowledgeRetrievalService,
                           DifficultyProfileService difficultyProfileService) {
        this.meetingRepository = meetingRepository;
        this.turnRepository = turnRepository;
        this.personaStateRepository = personaStateRepository;
        this.preparationRepository = preparationRepository;
        this.engagementRepository = engagementRepository;
        this.personaCatalogService = personaCatalogService;
        this.evidenceRepository = evidenceRepository;
        this.aiOrchestrationService = aiOrchestrationService;
        this.objectMapper = objectMapper;
        this.transcriptExportService = transcriptExportService;
        this.knowledgeRetrievalService = knowledgeRetrievalService;
        this.difficultyProfileService = difficultyProfileService;
    }

    @Transactional
    public MeetingResponse start(UUID engagementId, UUID userId) {
        Engagement engagement = engagementRepository.findByIdAndUserId(engagementId, userId)
                .orElseThrow(() -> new NotFoundException("Engagement", engagementId));

        if (engagement.getState() != EngagementState.PREPARING) {
            MeetingPreparation preparation = preparationRepository.findByEngagementId(engagementId).orElse(null);
            int readiness = preparation != null ? preparation.getReadinessScore() : 0;
            throw new PreparationNotReadyException(readiness);
        }

        engagement.transitionTo(EngagementState.IN_MEETING, "Meeting started");
        engagementRepository.save(engagement);

        Meeting meeting = Meeting.start(engagementId, engagement.getPersonaId());
        meetingRepository.save(meeting);

        DifficultyProfile profile = difficultyProfileService.forEngagement(engagement);
        personaStateRepository.findByEngagementId(engagementId)
                .orElseGet(() -> personaStateRepository.save(PersonaState.initial(engagementId, profile)));

        return MeetingResponse.from(meeting);
    }

    @Transactional(readOnly = true)
    public MeetingResponse get(UUID meetingId, UUID userId) {
        Meeting meeting = loadOwnedMeeting(meetingId, userId);
        return MeetingResponse.from(meeting);
    }

    @Transactional(readOnly = true)
    public List<ConversationTurnResponse> transcript(UUID meetingId, UUID userId) {
        loadOwnedMeeting(meetingId, userId);
        return turnRepository.findByMeetingIdOrderBySequenceAsc(meetingId).stream()
                .map(ConversationTurnResponse::from)
                .toList();
    }

    /**
     * Persists the learner's message, calls the persona-dialogue AI use case with a
     * grounded prompt, validates and applies the structured response, then persists
     * the persona's reply. Returns both turns plus the updated relationship state so
     * the caller (SSE controller) can stream them to the client.
     *
     * <p>Idempotent when {@code clientMessageId} is supplied: a retried/duplicated
     * request (double click, network retry) with the same key returns the
     * already-persisted turn pair instead of generating a second persona reply
     * (P0 fix — see incident: duplicate "Before we go further..." reply).
     */
    @Transactional
    public MeetingTurnResult sendMessage(UUID meetingId, UUID userId, String learnerMessage, String clientMessageId) {
        Meeting meeting = meetingRepository.findById(meetingId)
                .orElseThrow(() -> new NotFoundException("Meeting", meetingId));
        // Single ownership-validating fetch — the resulting Engagement is reused
        // below for scenarioId, instead of re-querying it a second time later in
        // this method as the previous implementation did (P0 perf fix: removes
        // one of ~11 sequential DB round trips this endpoint made per message).
        Engagement engagement = engagementRepository.findByIdAndUserId(meeting.getEngagementId(), userId)
                .orElseThrow(() -> new NotFoundException("Meeting", meetingId));
        if (clientMessageId != null && !clientMessageId.isBlank()) {
            MeetingTurnResult replay = tryReplayExisting(meeting, clientMessageId);
            if (replay != null) {
                return replay;
            }
        }
        if (meeting.getStatus() != MeetingStatus.IN_PROGRESS) {
            throw new InvalidMeetingStateException("Meeting is not in progress: " + meeting.getId());
        }
        DifficultyProfile profile = difficultyProfileService.forEngagement(engagement);

        List<ConversationTurn> existingTurns = turnRepository.findByMeetingIdOrderBySequenceAsc(meeting.getId());
        long learnerTurnCount = existingTurns.stream()
                .filter(turn -> turn.getActor() == ConversationActor.LEARNER)
                .count();
        if (learnerTurnCount >= profile.meetingTurnLimit()) {
            throw new MeetingTurnLimitReachedException(profile.meetingTurnLimit());
        }
        MeetingTurnResult contentDuplicateReplay =
                tryReplayRecentDuplicate(meeting, existingTurns, learnerMessage);
        if (contentDuplicateReplay != null) {
            return contentDuplicateReplay;
        }

        PersonaProfile persona = personaCatalogService.getPersona(meeting.getPersonaId());
        PersonaState state = personaStateRepository.findByEngagementId(meeting.getEngagementId())
                .orElseGet(() -> PersonaState.initial(meeting.getEngagementId(), profile));
        List<ResearchEvidence> evidence = evidenceRepository.findByEngagementId(meeting.getEngagementId());
        int transcriptStart = Math.max(0, existingTurns.size() - PROMPT_TRANSCRIPT_WINDOW);
        List<ConversationTurn> recentTurns = existingTurns.subList(transcriptStart, existingTurns.size());

        int nextSequence = existingTurns.size() + 1;
        ConversationTurn learnerTurn = ConversationTurn.learnerTurn(meeting.getId(), nextSequence, learnerMessage,
                clientMessageId);
        turnRepository.save(learnerTurn);

        var immediateTermination = MeetingSafetyPolicy.evaluate(learnerMessage, state)
                .filter(decision -> decision.reason() == MeetingTerminationReason.UNPROFESSIONAL_CONDUCT);
        if (immediateTermination.isPresent()) {
            ConversationTurn personaTurn = ConversationTurn.personaTurn(
                    meeting.getId(), nextSequence + 1,
                    "I find that language unacceptable and unprofessional. I am ending this meeting.",
                    "professionalism_breach");
            turnRepository.save(personaTurn);
            completeAutomatically(meeting, engagement, immediateTermination.get());
            return new MeetingTurnResult(
                    ConversationTurnResponse.from(learnerTurn),
                    ConversationTurnResponse.from(personaTurn),
                    PersonaStateResponse.from(state),
                    List.of("professionalism_breach"),
                    MeetingTerminationResponse.from(immediateTermination.get()));
        }

        List<String> retrievedKnowledge = knowledgeRetrievalService.retrieveRelevantPassages(
                KnowledgeCollection.SCENARIO_TRUTH, engagement.getScenarioId(), persona.getId(), learnerMessage);

        String prompt = PersonaPromptAssembler.assemble(persona, state, evidence, retrievedKnowledge, recentTurns,
                learnerMessage, profile);
        PersonaTurnResponseParser parser = new PersonaTurnResponseParser(objectMapper, Set.of());

        PersonaTurnResponse aiResponse = aiOrchestrationService.execute(
                "persona_dialogue",
                meeting.getEngagementId(),
                prompt,
                PROMPT_VERSION,
                parser,
                () -> PersonaTurnResponse.safeFallback(
                        "Sorry, could you repeat that? I want to make sure I understand you correctly."));

        PersonaStateEngine.apply(state, aiResponse, profile);
        personaStateRepository.save(state);

        String signals = String.join(",", combineSignals(aiResponse));
        ConversationTurn personaTurn = ConversationTurn.personaTurn(
                meeting.getId(), nextSequence + 1, aiResponse.spokenResponse(), signals);
        turnRepository.save(personaTurn);

        var relationshipTermination = MeetingSafetyPolicy.evaluate(learnerMessage, state)
                .filter(decision -> decision.reason() == MeetingTerminationReason.RELATIONSHIP_THRESHOLD_BREACH);
        if (relationshipTermination.isPresent()) {
            completeAutomatically(meeting, engagement, relationshipTermination.get());
        }

        return new MeetingTurnResult(
                ConversationTurnResponse.from(learnerTurn),
                ConversationTurnResponse.from(personaTurn),
                PersonaStateResponse.from(state),
                aiResponse.meetingSignals(),
                relationshipTermination.map(MeetingTerminationResponse::from).orElse(null));
    }

    /**
     * Content-based duplicate guard, independent of {@code clientMessageId} (that key
     * is generated fresh per browser call, so it does not catch a genuinely separate
     * HTTP request that happens to carry the exact same learner text — e.g. a client
     * that believed a slow request had failed and resent the identical message).
     * If the most recent learner turn has identical (trimmed) content and was
     * created within {@link #DUPLICATE_WINDOW}, and its persona reply already
     * exists, replay that exchange instead of asking the AI to answer the same
     * question twice in a row (P0 fix — duplicate transcript bubbles incident, made
     * more likely once real multi-second AI latency replaced the instant mock
     * gateway, increasing the odds of a learner resending on perceived timeout).
     */
    private MeetingTurnResult tryReplayRecentDuplicate(Meeting meeting, List<ConversationTurn> existingTurns,
                                                        String learnerMessage) {
        if (existingTurns.size() < 2) {
            return null;
        }
        ConversationTurn last = existingTurns.get(existingTurns.size() - 1);
        ConversationTurn secondLast = existingTurns.get(existingTurns.size() - 2);
        if (last.getActor() != ConversationActor.PERSONA || secondLast.getActor() != ConversationActor.LEARNER) {
            return null;
        }
        if (!secondLast.getContent().trim().equals(learnerMessage.trim())) {
            return null;
        }
        if (java.time.Duration.between(secondLast.getCreatedAt(), java.time.Instant.now())
                .compareTo(DUPLICATE_WINDOW) > 0) {
            return null;
        }
        PersonaState state = personaStateRepository.findByEngagementId(meeting.getEngagementId())
                .orElseGet(() -> PersonaState.initial(meeting.getEngagementId()));
        List<String> signals = last.getSignals() == null || last.getSignals().isBlank()
                ? List.of()
                : List.of(last.getSignals().split(","));
        return new MeetingTurnResult(
                ConversationTurnResponse.from(secondLast),
                ConversationTurnResponse.from(last),
                PersonaStateResponse.from(state),
                signals,
                MeetingTerminationResponse.from(meeting));
    }

    /**
     * If a learner turn with this {@code clientMessageId} was already persisted
     * (previous attempt succeeded but the client didn't see the response, or the
     * client double-submitted), returns the existing turn pair without calling the
     * AI again. Returns {@code null} if no prior turn exists for this key.
     */
    private MeetingTurnResult tryReplayExisting(Meeting meeting, String clientMessageId) {
        return turnRepository.findByMeetingIdAndClientMessageId(meeting.getId(), clientMessageId)
                .map(existingLearnerTurn -> {
                    List<ConversationTurn> turns = turnRepository.findByMeetingIdOrderBySequenceAsc(meeting.getId());
                    ConversationTurn existingPersonaTurn = turns.stream()
                            .filter(t -> t.getActor() == ConversationActor.PERSONA
                                    && t.getSequence() == existingLearnerTurn.getSequence() + 1)
                            .findFirst()
                            .orElse(null);
                    if (existingPersonaTurn == null) {
                        return null;
                    }
                    PersonaState state = personaStateRepository.findByEngagementId(meeting.getEngagementId())
                            .orElseGet(() -> PersonaState.initial(meeting.getEngagementId()));
                    List<String> signals = existingPersonaTurn.getSignals() == null
                            || existingPersonaTurn.getSignals().isBlank()
                            ? List.of()
                            : List.of(existingPersonaTurn.getSignals().split(","));
                    return new MeetingTurnResult(
                            ConversationTurnResponse.from(existingLearnerTurn),
                            ConversationTurnResponse.from(existingPersonaTurn),
                            PersonaStateResponse.from(state),
                            signals,
                            MeetingTerminationResponse.from(meeting));
                })
                .orElse(null);
    }

    @Transactional
    public MeetingResponse complete(UUID meetingId, UUID userId) {
        Meeting meeting = loadOwnedMeeting(meetingId, userId);
        Engagement engagement = engagementRepository.findByIdAndUserId(meeting.getEngagementId(), userId)
                .orElseThrow(() -> new NotFoundException("Engagement", meeting.getEngagementId()));

        if (meeting.getStatus() == MeetingStatus.COMPLETED) {
            return MeetingResponse.from(meeting);
        }

        PersonaState state = personaStateRepository.findByEngagementId(meeting.getEngagementId())
                .orElseGet(() -> PersonaState.initial(meeting.getEngagementId()));
        MeetingCompletionDecision decision = MeetingCompletionPolicy.evaluate(state);
        List<ConversationTurn> turns = turnRepository.findByMeetingIdOrderBySequenceAsc(meetingId);
        MeetingDebriefNarrative debrief = aiOrchestrationService.execute(
                "meeting_debrief",
                meeting.getEngagementId(),
                buildDebriefPrompt(state, decision, turns),
                PROMPT_VERSION,
                new MeetingDebriefParser(objectMapper),
                () -> MeetingDebriefNarrative.fallback(decision.passed(), decision.unmetRequirements()));

        meeting.complete(decision.outcome(), debrief.feedback(), debrief.tips());
        String storageReference = transcriptExportService.export(meeting);
        meeting.recordTranscriptExport(storageReference);
        meetingRepository.save(meeting);

        engagement.transitionTo(
                decision.passed() ? EngagementState.DISCOVERY_COMPLETE : EngagementState.MEETING_FAILED,
                decision.passed() ? "Meeting completed: passed relationship gate" : "Meeting completed: relationship gate failed");
        engagementRepository.save(engagement);

        return MeetingResponse.from(meeting);
    }

    private Meeting loadOwnedMeeting(UUID meetingId, UUID userId) {
        Meeting meeting = meetingRepository.findById(meetingId)
                .orElseThrow(() -> new NotFoundException("Meeting", meetingId));
        engagementRepository.findByIdAndUserId(meeting.getEngagementId(), userId)
                .orElseThrow(() -> new NotFoundException("Meeting", meetingId));
        return meeting;
    }

    private List<String> combineSignals(PersonaTurnResponse response) {
        List<String> signals = new java.util.ArrayList<>(response.meetingSignals());
        if (response.objectionRaised() != null) {
            signals.add("objection:" + response.objectionRaised());
        }
        return signals;
    }

    private void completeAutomatically(Meeting meeting, Engagement engagement,
                                       MeetingTerminationDecision decision) {
        meeting.complete(MeetingCompletionOutcome.FAILED, decision.message(), decision.retryGuidance(), decision.reason());
        String storageReference = transcriptExportService.export(meeting);
        meeting.recordTranscriptExport(storageReference);
        meetingRepository.save(meeting);

        engagement.transitionTo(EngagementState.MEETING_FAILED,
                "Meeting automatically failed: " + decision.reason().name());
        engagementRepository.save(engagement);
    }

    private String buildDebriefPrompt(PersonaState state, MeetingCompletionDecision decision,
                                      List<ConversationTurn> turns) {
        String transcript = turns.stream()
                .skip(Math.max(0, turns.size() - 8))
                .map(turn -> turn.getActor().name() + ": " + turn.getContent())
                .collect(java.util.stream.Collectors.joining("\n"));
        return """
                You are a consulting-skills coach. The backend has already decided the meeting outcome.
                Do not challenge or change it. Write concise coaching grounded only in the performance data and transcript.
                Return ONLY JSON: {"feedback": string, "tips": [string, string, string]}.

                Outcome: %s
                Relationship scores: trust=%d, interest=%d, patience=%d. Required score for every dimension: %d.
                Unmet requirements: %s
                Recent transcript:
                %s
                """.formatted(decision.outcome(), state.getTrust(), state.getInterest(), state.getPatience(),
                MeetingCompletionPolicy.REQUIRED_SCORE, decision.unmetRequirements(), transcript);
    }

    public static class MeetingTurnLimitReachedException extends DomainException {
        public MeetingTurnLimitReachedException(int limit) {
            super("Meeting turn limit (%d) reached. Complete the meeting to receive your assessment.".formatted(limit));
        }
    }
}

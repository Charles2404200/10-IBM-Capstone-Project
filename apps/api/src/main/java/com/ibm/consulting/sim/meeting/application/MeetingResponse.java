package com.ibm.consulting.sim.meeting.application;

import com.ibm.consulting.sim.meeting.domain.Meeting;
import com.ibm.consulting.sim.meeting.domain.MeetingCompletionPolicy;
import com.ibm.consulting.sim.meeting.domain.MeetingInteractionMode;
import com.ibm.consulting.sim.scenario.domain.DifficultyProfile;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record MeetingResponse(UUID id, UUID engagementId, UUID personaId, String status,
                               String interactionMode, int meetingThreshold,
                               Instant completedAt, String transcriptStorageReference,
                               String completionOutcome, String debriefFeedback, java.util.List<String> debriefTips,
                               String terminationReason, String terminationMessage,
                               boolean meetingRetryAvailable, int meetingRetriesRemaining,
                               java.util.List<MeetingBehaviourFeedbackResponse> behaviourLedger) {
    /** Source-compatible constructor for callers compiled against the pre-termination response shape. */
    public MeetingResponse(UUID id, UUID engagementId, UUID personaId, String status,
                           Instant completedAt, String transcriptStorageReference,
                           String completionOutcome, String debriefFeedback, java.util.List<String> debriefTips) {
        this(id, engagementId, personaId, status, MeetingInteractionMode.GUIDED.name(), MeetingCompletionPolicy.REQUIRED_SCORE,
                completedAt, transcriptStorageReference,
                completionOutcome, debriefFeedback, debriefTips, null, null, false, 0, List.of());
    }

    public static MeetingResponse from(Meeting m) {
        return from(m, null, false, 0);
    }

    public static MeetingResponse from(Meeting m, DifficultyProfile profile,
                                       boolean meetingRetryAvailable, int meetingRetriesRemaining) {
        MeetingInteractionMode interactionMode = profile == null
                ? MeetingInteractionMode.GUIDED
                : MeetingInteractionMode.forDifficulty(profile.level());
        return new MeetingResponse(m.getId(), m.getEngagementId(), m.getPersonaId(), m.getStatus().name(),
                interactionMode.name(), MeetingCompletionPolicy.requiredScoreFor(profile),
                m.getCompletedAt(), m.getTranscriptStorageReference(),
                m.getCompletionOutcome() == null ? null : m.getCompletionOutcome().name(),
                m.getDebriefFeedback(), List.copyOf(m.getDebriefTips()),
                m.getTerminationReason() == null ? null : m.getTerminationReason().name(),
                m.getTerminationMessage(), meetingRetryAvailable, meetingRetriesRemaining,
                m.getBehaviourLedger().stream().map(MeetingBehaviourFeedbackResponse::from).toList());
    }

    public static MeetingResponse from(Meeting m, boolean meetingRetryAvailable, int meetingRetriesRemaining) {
        return from(m, null, meetingRetryAvailable, meetingRetriesRemaining);
    }
}

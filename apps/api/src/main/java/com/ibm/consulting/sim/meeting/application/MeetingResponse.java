package com.ibm.consulting.sim.meeting.application;

import com.ibm.consulting.sim.meeting.domain.Meeting;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record MeetingResponse(UUID id, UUID engagementId, UUID personaId, String status,
                               Instant completedAt, String transcriptStorageReference,
                               String completionOutcome, String debriefFeedback, java.util.List<String> debriefTips,
                               String terminationReason, String terminationMessage,
                               boolean meetingRetryAvailable, int meetingRetriesRemaining,
                               java.util.List<MeetingBehaviourFeedbackResponse> behaviourLedger) {
    /** Source-compatible constructor for callers compiled against the pre-termination response shape. */
    public MeetingResponse(UUID id, UUID engagementId, UUID personaId, String status,
                           Instant completedAt, String transcriptStorageReference,
                           String completionOutcome, String debriefFeedback, java.util.List<String> debriefTips) {
        this(id, engagementId, personaId, status, completedAt, transcriptStorageReference,
                completionOutcome, debriefFeedback, debriefTips, null, null, false, 0, List.of());
    }

    public static MeetingResponse from(Meeting m) {
        return new MeetingResponse(m.getId(), m.getEngagementId(), m.getPersonaId(), m.getStatus().name(),
                m.getCompletedAt(), m.getTranscriptStorageReference(),
                m.getCompletionOutcome() == null ? null : m.getCompletionOutcome().name(),
                m.getDebriefFeedback(), List.copyOf(m.getDebriefTips()),
                m.getTerminationReason() == null ? null : m.getTerminationReason().name(),
                m.getTerminationMessage(), false, 0,
                m.getBehaviourLedger().stream().map(MeetingBehaviourFeedbackResponse::from).toList());
    }

    public static MeetingResponse from(Meeting m, boolean meetingRetryAvailable, int meetingRetriesRemaining) {
        MeetingResponse response = from(m);
        return new MeetingResponse(response.id(), response.engagementId(), response.personaId(), response.status(),
                response.completedAt(), response.transcriptStorageReference(), response.completionOutcome(),
                response.debriefFeedback(), response.debriefTips(), response.terminationReason(),
                response.terminationMessage(), meetingRetryAvailable, meetingRetriesRemaining, response.behaviourLedger());
    }
}

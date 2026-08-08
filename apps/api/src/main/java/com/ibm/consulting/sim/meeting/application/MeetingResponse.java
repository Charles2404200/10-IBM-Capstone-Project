package com.ibm.consulting.sim.meeting.application;

import com.ibm.consulting.sim.meeting.domain.Meeting;

import java.time.Instant;
import java.util.UUID;

public record MeetingResponse(UUID id, UUID engagementId, UUID personaId, String status,
                               Instant completedAt, String transcriptStorageReference) {
    public static MeetingResponse from(Meeting m) {
        return new MeetingResponse(m.getId(), m.getEngagementId(), m.getPersonaId(), m.getStatus().name(),
                m.getCompletedAt(), m.getTranscriptStorageReference());
    }
}

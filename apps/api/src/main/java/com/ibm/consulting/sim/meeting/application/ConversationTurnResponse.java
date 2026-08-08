package com.ibm.consulting.sim.meeting.application;

import com.ibm.consulting.sim.meeting.domain.ConversationTurn;

import java.time.Instant;
import java.util.UUID;

public record ConversationTurnResponse(UUID id, UUID meetingId, String actor, String content,
                                        int sequence, String signals, Instant createdAt) {
    public static ConversationTurnResponse from(ConversationTurn t) {
        return new ConversationTurnResponse(t.getId(), t.getMeetingId(), t.getActor().name(),
                t.getContent(), t.getSequence(), t.getSignals(), t.getCreatedAt());
    }
}

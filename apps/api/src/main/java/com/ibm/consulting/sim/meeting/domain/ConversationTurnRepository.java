package com.ibm.consulting.sim.meeting.domain;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ConversationTurnRepository {
    ConversationTurn save(ConversationTurn turn);
    List<ConversationTurn> findByMeetingIdOrderBySequenceAsc(UUID meetingId);
    int countByMeetingId(UUID meetingId);
    Optional<ConversationTurn> findByMeetingIdAndClientMessageId(UUID meetingId, String clientMessageId);
}

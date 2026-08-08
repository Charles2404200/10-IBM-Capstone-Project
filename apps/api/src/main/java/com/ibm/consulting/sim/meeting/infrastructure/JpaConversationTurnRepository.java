package com.ibm.consulting.sim.meeting.infrastructure;

import com.ibm.consulting.sim.meeting.domain.ConversationTurn;
import com.ibm.consulting.sim.meeting.domain.ConversationTurnRepository;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
interface SpringDataConversationTurnRepository extends JpaRepository<ConversationTurn, UUID> {
    List<ConversationTurn> findByMeetingIdOrderBySequenceAsc(UUID meetingId);
    int countByMeetingId(UUID meetingId);
    Optional<ConversationTurn> findByMeetingIdAndClientMessageId(UUID meetingId, String clientMessageId);
}

@Repository
class JpaConversationTurnRepository implements ConversationTurnRepository {

    private final SpringDataConversationTurnRepository repo;

    JpaConversationTurnRepository(SpringDataConversationTurnRepository repo) {
        this.repo = repo;
    }

    @Override public ConversationTurn save(ConversationTurn turn) { return repo.save(turn); }
    @Override public List<ConversationTurn> findByMeetingIdOrderBySequenceAsc(UUID meetingId) {
        return repo.findByMeetingIdOrderBySequenceAsc(meetingId);
    }
    @Override public int countByMeetingId(UUID meetingId) { return repo.countByMeetingId(meetingId); }
    @Override public Optional<ConversationTurn> findByMeetingIdAndClientMessageId(UUID meetingId, String clientMessageId) {
        return repo.findByMeetingIdAndClientMessageId(meetingId, clientMessageId);
    }
}

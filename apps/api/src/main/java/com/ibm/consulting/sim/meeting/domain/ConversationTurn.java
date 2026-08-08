package com.ibm.consulting.sim.meeting.domain;

import com.ibm.consulting.sim.shared.domain.BaseEntity;
import jakarta.persistence.*;

import java.util.UUID;

/** Immutable transcript entry. Never updated once persisted (audit + replay, §4.2). */
@Entity
@Table(name = "conversation_turns")
public class ConversationTurn extends BaseEntity {

    @Column(nullable = false)
    private UUID meetingId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ConversationActor actor;

    @Column(columnDefinition = "text", nullable = false)
    private String content;

    @Column(nullable = false)
    private int sequence;

    /** Comma-separated objection/meeting-signal tags for persona turns; null for learner turns. */
    @Column(columnDefinition = "text")
    private String signals;

    /**
     * Optional client-supplied idempotency key (learner turns only). When a
     * "send message" request is retried or double-submitted with the same
     * key, {@link com.ibm.consulting.sim.meeting.application.MeetingService}
     * detects the existing turn and returns it instead of generating a
     * second persona reply.
     */
    @Column(name = "client_message_id", length = 100)
    private String clientMessageId;

    protected ConversationTurn() {}

    public static ConversationTurn learnerTurn(UUID meetingId, int sequence, String content) {
        return learnerTurn(meetingId, sequence, content, null);
    }

    public static ConversationTurn learnerTurn(UUID meetingId, int sequence, String content, String clientMessageId) {
        ConversationTurn t = new ConversationTurn();
        t.meetingId = meetingId;
        t.actor = ConversationActor.LEARNER;
        t.sequence = sequence;
        t.content = content;
        t.clientMessageId = clientMessageId;
        return t;
    }

    public static ConversationTurn personaTurn(UUID meetingId, int sequence, String content, String signals) {
        ConversationTurn t = new ConversationTurn();
        t.meetingId = meetingId;
        t.actor = ConversationActor.PERSONA;
        t.sequence = sequence;
        t.content = content;
        t.signals = signals;
        return t;
    }

    public UUID getMeetingId() { return meetingId; }
    public ConversationActor getActor() { return actor; }
    public String getContent() { return content; }
    public int getSequence() { return sequence; }
    public String getSignals() { return signals; }
    public String getClientMessageId() { return clientMessageId; }
}

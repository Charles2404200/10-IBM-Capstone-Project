package com.ibm.consulting.sim.outreach.domain;

import com.ibm.consulting.sim.shared.domain.BaseEntity;
import jakarta.persistence.*;

import java.util.UUID;

@Entity
@Table(name = "outreach_attempts")
public class OutreachAttempt extends BaseEntity {

    @Column(nullable = false)
    private UUID engagementId;

    @Column(nullable = false)
    private int attemptNumber;

    @Column(nullable = false)
    private String subject;

    @Column(columnDefinition = "text", nullable = false)
    private String body;

    @Column(columnDefinition = "text")
    private String clientReply;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private OutreachOutcome outcome;

    // Scores 0–100
    private Integer scorePersonalisation;
    private Integer scoreRelevance;
    private Integer scoreClarity;
    private Integer scoreCallToAction;

    protected OutreachAttempt() {}

    public static OutreachAttempt create(UUID engagementId, int attemptNumber,
                                         String subject, String body) {
        OutreachAttempt a = new OutreachAttempt();
        a.engagementId = engagementId;
        a.attemptNumber = attemptNumber;
        a.subject = subject;
        a.body = body;
        a.outcome = OutreachOutcome.PENDING;
        return a;
    }

    public void resolve(String clientReply, OutreachOutcome outcome,
                        int personalisation, int relevance, int clarity, int callToAction) {
        this.clientReply = clientReply;
        this.outcome = outcome;
        this.scorePersonalisation = personalisation;
        this.scoreRelevance = relevance;
        this.scoreClarity = clarity;
        this.scoreCallToAction = callToAction;
    }

    public UUID getEngagementId() { return engagementId; }
    public int getAttemptNumber() { return attemptNumber; }
    public String getSubject() { return subject; }
    public String getBody() { return body; }
    public String getClientReply() { return clientReply; }
    public OutreachOutcome getOutcome() { return outcome; }
    public Integer getScorePersonalisation() { return scorePersonalisation; }
    public Integer getScoreRelevance() { return scoreRelevance; }
    public Integer getScoreClarity() { return scoreClarity; }
    public Integer getScoreCallToAction() { return scoreCallToAction; }
}

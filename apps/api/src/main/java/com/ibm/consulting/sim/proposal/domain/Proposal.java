package com.ibm.consulting.sim.proposal.domain;

import com.ibm.consulting.sim.shared.domain.BaseEntity;
import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "proposals")
public class Proposal extends BaseEntity {

    @Column(nullable = false, unique = true)
    private UUID engagementId;

    @Column(columnDefinition = "text", nullable = false)
    private String problemStatement;

    @ElementCollection
    @CollectionTable(name = "proposal_components", joinColumns = @JoinColumn(name = "proposal_id"))
    @Column(name = "item", columnDefinition = "text")
    @OrderColumn(name = "position")
    private List<String> components = new ArrayList<>();

    @Column(nullable = false, precision = 14, scale = 2)
    private BigDecimal budget;

    @Column(nullable = false)
    private int timelineWeeks;

    @Column(nullable = false)
    private int alignmentScore;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ProposalDecision decision;

    @Column(columnDefinition = "text")
    private String decisionRationale;

    @Column(nullable = false)
    private Instant submittedAt;

    protected Proposal() {}

    public static Proposal submit(UUID engagementId, String problemStatement, List<String> components,
                                   BigDecimal budget, int timelineWeeks) {
        Proposal p = new Proposal();
        p.engagementId = engagementId;
        p.problemStatement = problemStatement;
        p.components = new ArrayList<>(components);
        p.budget = budget;
        p.timelineWeeks = timelineWeeks;
        p.decision = ProposalDecision.PENDING;
        p.submittedAt = Instant.now();
        return p;
    }

    public void resolve(int alignmentScore, ProposalDecision decision, String rationale) {
        this.alignmentScore = alignmentScore;
        this.decision = decision;
        this.decisionRationale = rationale;
    }

    public UUID getEngagementId() { return engagementId; }
    public String getProblemStatement() { return problemStatement; }
    public List<String> getComponents() { return Collections.unmodifiableList(components); }
    public BigDecimal getBudget() { return budget; }
    public int getTimelineWeeks() { return timelineWeeks; }
    public int getAlignmentScore() { return alignmentScore; }
    public ProposalDecision getDecision() { return decision; }
    public String getDecisionRationale() { return decisionRationale; }
    public Instant getSubmittedAt() { return submittedAt; }
}

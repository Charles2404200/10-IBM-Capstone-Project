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

    @Column(columnDefinition = "text")
    private String solutionStrategy;

    @ElementCollection
    @CollectionTable(name = "proposal_components", joinColumns = @JoinColumn(name = "proposal_id"))
    @Column(name = "item", columnDefinition = "text")
    @OrderColumn(name = "position")
    private List<String> components = new ArrayList<>();

    @Column(nullable = false, precision = 14, scale = 2)
    private BigDecimal budget;

    @Column(nullable = false)
    private int timelineWeeks;

    @Column(length = 20)
    private String budgetConfidence;

    @Column(columnDefinition = "text")
    private String budgetSource;

    @ElementCollection
    @CollectionTable(name = "proposal_business_outcomes", joinColumns = @JoinColumn(name = "proposal_id"))
    @OrderColumn(name = "position")
    private List<ProposalBusinessOutcome> businessOutcomes = new ArrayList<>();

    @ElementCollection
    @CollectionTable(name = "proposal_milestones", joinColumns = @JoinColumn(name = "proposal_id"))
    @OrderColumn(name = "position")
    private List<ProposalMilestone> milestones = new ArrayList<>();

    @ElementCollection
    @CollectionTable(name = "proposal_risks", joinColumns = @JoinColumn(name = "proposal_id"))
    @OrderColumn(name = "position")
    private List<ProposalRisk> risks = new ArrayList<>();

    @ElementCollection
    @CollectionTable(name = "proposal_assumptions", joinColumns = @JoinColumn(name = "proposal_id"))
    @Column(name = "item", columnDefinition = "text")
    @OrderColumn(name = "position")
    private List<String> assumptions = new ArrayList<>();

    @ElementCollection
    @CollectionTable(name = "proposal_evidence_links", joinColumns = @JoinColumn(name = "proposal_id"))
    @OrderColumn(name = "position")
    private List<ProposalEvidenceLink> evidenceLinks = new ArrayList<>();

    @Column(nullable = false)
    private int alignmentScore;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ProposalDecision decision;

    @Column(columnDefinition = "text")
    private String decisionRationale;

    @Column(columnDefinition = "text")
    private String clientResponse;

    @Enumerated(EnumType.STRING)
    private ClientDecisionOutcome clientDecisionOutcome;

    private Integer decisionConfidence;

    private Integer learnerPerformanceScore;

    @ElementCollection
    @CollectionTable(name = "proposal_decision_dimensions", joinColumns = @JoinColumn(name = "proposal_id"))
    @OrderColumn(name = "position")
    private List<ProposalDecisionDimension> decisionDimensions = new ArrayList<>();

    @ElementCollection
    @CollectionTable(name = "proposal_decision_insights", joinColumns = @JoinColumn(name = "proposal_id"))
    @OrderColumn(name = "position")
    private List<ProposalDecisionInsight> decisionInsights = new ArrayList<>();

    @ElementCollection
    @CollectionTable(name = "proposal_evidence_impacts", joinColumns = @JoinColumn(name = "proposal_id"))
    @OrderColumn(name = "position")
    private List<ProposalEvidenceImpact> evidenceImpacts = new ArrayList<>();

    @Column(nullable = false)
    private Instant submittedAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ProposalStatus status = ProposalStatus.DRAFT;

    protected Proposal() {}

    public static Proposal draft(UUID engagementId, ProposalDraftContent content) {
        Proposal p = new Proposal();
        p.engagementId = engagementId;
        p.apply(content);
        p.decision = ProposalDecision.PENDING;
        p.status = ProposalStatus.DRAFT;
        return p;
    }

    public void updateDraft(ProposalDraftContent content) {
        if (status == ProposalStatus.SUBMITTED) throw new IllegalStateException("Submitted proposals are immutable");
        apply(content);
    }

    public void submit() {
        if (status == ProposalStatus.SUBMITTED) throw new IllegalStateException("Proposal has already been submitted");
        status = ProposalStatus.SUBMITTED;
        submittedAt = Instant.now();
    }

    public void resolve(ProposalDecisionSnapshot snapshot, String clientResponse) {
        this.alignmentScore = snapshot.decisionScore();
        this.decision = snapshot.accepted() ? ProposalDecision.WON : ProposalDecision.LOST;
        this.decisionRationale = snapshot.rationale();
        this.clientResponse = clientResponse;
        this.clientDecisionOutcome = snapshot.outcome();
        this.decisionConfidence = snapshot.decisionConfidence();
        this.learnerPerformanceScore = snapshot.learnerPerformanceScore();
        this.decisionDimensions = new ArrayList<>(snapshot.dimensions());
        this.decisionInsights = new ArrayList<>(snapshot.insights());
        this.evidenceImpacts = new ArrayList<>(snapshot.evidenceImpacts());
    }

    private void apply(ProposalDraftContent content) {
        this.problemStatement = content.problemStatement();
        this.solutionStrategy = content.solutionStrategy();
        this.components = new ArrayList<>(content.components());
        this.budget = content.budget();
        this.timelineWeeks = content.timelineWeeks();
        this.budgetConfidence = content.budgetConfidence();
        this.budgetSource = content.budgetSource();
        this.businessOutcomes = new ArrayList<>(content.businessOutcomes());
        this.milestones = new ArrayList<>(content.milestones());
        this.risks = new ArrayList<>(content.risks());
        this.assumptions = new ArrayList<>(content.assumptions());
        this.evidenceLinks = new ArrayList<>(content.evidenceLinks());
    }

    public UUID getEngagementId() { return engagementId; }
    public String getProblemStatement() { return problemStatement; }
    public String getSolutionStrategy() { return solutionStrategy; }
    public List<String> getComponents() { return Collections.unmodifiableList(components); }
    public BigDecimal getBudget() { return budget; }
    public int getTimelineWeeks() { return timelineWeeks; }
    public String getBudgetConfidence() { return budgetConfidence; }
    public String getBudgetSource() { return budgetSource; }
    public List<ProposalBusinessOutcome> getBusinessOutcomes() { return List.copyOf(businessOutcomes); }
    public List<ProposalMilestone> getMilestones() { return List.copyOf(milestones); }
    public List<ProposalRisk> getRisks() { return List.copyOf(risks); }
    public List<String> getAssumptions() { return List.copyOf(assumptions); }
    public List<ProposalEvidenceLink> getEvidenceLinks() { return List.copyOf(evidenceLinks); }
    public int getAlignmentScore() { return alignmentScore; }
    public ProposalDecision getDecision() { return decision; }
    public String getDecisionRationale() { return decisionRationale; }
    public String getClientResponse() { return clientResponse; }
    public ClientDecisionOutcome getClientDecisionOutcome() { return clientDecisionOutcome; }
    public Integer getDecisionConfidence() { return decisionConfidence; }
    public Integer getLearnerPerformanceScore() { return learnerPerformanceScore; }
    public List<ProposalDecisionDimension> getDecisionDimensions() { return List.copyOf(decisionDimensions); }
    public List<ProposalDecisionInsight> getDecisionInsights() { return List.copyOf(decisionInsights); }
    public List<ProposalEvidenceImpact> getEvidenceImpacts() { return List.copyOf(evidenceImpacts); }
    public Instant getSubmittedAt() { return submittedAt; }
    public ProposalStatus getStatus() { return status; }
}

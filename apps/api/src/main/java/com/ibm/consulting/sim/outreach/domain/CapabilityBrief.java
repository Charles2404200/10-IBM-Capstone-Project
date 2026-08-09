package com.ibm.consulting.sim.outreach.domain;

import com.ibm.consulting.sim.shared.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

import java.util.UUID;

/** A traceable client-requested document, owned by one engagement. */
@Entity
@Table(name = "capability_briefs")
public class CapabilityBrief extends BaseEntity {

    @Column(nullable = false, unique = true)
    private UUID engagementId;

    @Column(columnDefinition = "text", nullable = false)
    private String relevantExperience;

    @Column(columnDefinition = "text", nullable = false)
    private String approach;

    @Column(columnDefinition = "text", nullable = false)
    private String caseExample;

    @Column(columnDefinition = "text", nullable = false)
    private String clientFit;

    @Column(columnDefinition = "text")
    private String clientReply;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private OutreachOutcome outcome;

    private Integer scoreClientFit;
    private Integer scoreIndustryRelevance;
    private Integer scoreEvidenceQuality;
    private Integer scoreClarity;
    private Integer scoreCredibility;

    protected CapabilityBrief() {}

    public static CapabilityBrief create(UUID engagementId, String relevantExperience, String approach,
                                         String caseExample, String clientFit) {
        CapabilityBrief brief = new CapabilityBrief();
        brief.engagementId = engagementId;
        brief.updateContent(relevantExperience, approach, caseExample, clientFit);
        brief.outcome = OutreachOutcome.PENDING;
        return brief;
    }

    public void review(String clientReply, OutreachOutcome outcome, int clientFitScore,
                       int industryRelevance, int evidenceQuality, int clarity, int credibility) {
        this.clientReply = clientReply;
        this.outcome = outcome;
        this.scoreClientFit = clientFitScore;
        this.scoreIndustryRelevance = industryRelevance;
        this.scoreEvidenceQuality = evidenceQuality;
        this.scoreClarity = clarity;
        this.scoreCredibility = credibility;
    }

    public void updateContent(String relevantExperience, String approach, String caseExample, String clientFit) {
        this.relevantExperience = relevantExperience;
        this.approach = approach;
        this.caseExample = caseExample;
        this.clientFit = clientFit;
    }

    public UUID getEngagementId() { return engagementId; }
    public String getRelevantExperience() { return relevantExperience; }
    public String getApproach() { return approach; }
    public String getCaseExample() { return caseExample; }
    public String getClientFit() { return clientFit; }
    public String getClientReply() { return clientReply; }
    public OutreachOutcome getOutcome() { return outcome; }
    public Integer getScoreClientFit() { return scoreClientFit; }
    public Integer getScoreIndustryRelevance() { return scoreIndustryRelevance; }
    public Integer getScoreEvidenceQuality() { return scoreEvidenceQuality; }
    public Integer getScoreClarity() { return scoreClarity; }
    public Integer getScoreCredibility() { return scoreCredibility; }
}

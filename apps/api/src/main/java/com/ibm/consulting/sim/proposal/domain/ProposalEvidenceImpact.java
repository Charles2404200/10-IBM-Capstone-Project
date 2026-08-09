package com.ibm.consulting.sim.proposal.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

@Embeddable
public class ProposalEvidenceImpact {
    @Column(name = "claim", columnDefinition = "text")
    private String claim;
    @Column(name = "support_level", length = 30)
    private String supportLevel;
    @Column(name = "explanation", columnDefinition = "text")
    private String explanation;

    protected ProposalEvidenceImpact() {}
    public ProposalEvidenceImpact(String claim, String supportLevel, String explanation) {
        this.claim = claim;
        this.supportLevel = supportLevel;
        this.explanation = explanation;
    }
    public String getClaim() { return claim; }
    public String getSupportLevel() { return supportLevel; }
    public String getExplanation() { return explanation; }
}

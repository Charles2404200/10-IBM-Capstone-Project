package com.ibm.consulting.sim.proposal.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

@Embeddable
public class ProposalDecisionInsight {
    @Column(name = "category", length = 30)
    private String category;
    @Column(name = "detail", columnDefinition = "text")
    private String detail;

    protected ProposalDecisionInsight() {}
    public ProposalDecisionInsight(String category, String detail) { this.category = category; this.detail = detail; }
    public String getCategory() { return category; }
    public String getDetail() { return detail; }
}

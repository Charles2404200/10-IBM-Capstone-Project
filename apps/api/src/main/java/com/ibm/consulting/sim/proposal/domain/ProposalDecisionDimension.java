package com.ibm.consulting.sim.proposal.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

@Embeddable
public class ProposalDecisionDimension {
    @Column(name = "dimension", length = 80)
    private String dimension;
    @Column(name = "score")
    private int score;
    @Column(name = "interpretation", columnDefinition = "text")
    private String interpretation;

    protected ProposalDecisionDimension() {}
    public ProposalDecisionDimension(String dimension, int score, String interpretation) {
        this.dimension = dimension;
        this.score = score;
        this.interpretation = interpretation;
    }
    public String getDimension() { return dimension; }
    public int getScore() { return score; }
    public String getInterpretation() { return interpretation; }
}

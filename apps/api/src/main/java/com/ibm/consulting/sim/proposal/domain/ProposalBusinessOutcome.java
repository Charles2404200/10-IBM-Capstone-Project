package com.ibm.consulting.sim.proposal.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

@Embeddable
public class ProposalBusinessOutcome {
    @Column(name = "outcome", columnDefinition = "text")
    private String outcome;
    @Column(name = "metric", columnDefinition = "text")
    private String metric;
    @Column(name = "target", columnDefinition = "text")
    private String target;

    protected ProposalBusinessOutcome() {}
    public ProposalBusinessOutcome(String outcome, String metric, String target) {
        this.outcome = outcome;
        this.metric = metric;
        this.target = target;
    }
    public String getOutcome() { return outcome; }
    public String getMetric() { return metric; }
    public String getTarget() { return target; }
}

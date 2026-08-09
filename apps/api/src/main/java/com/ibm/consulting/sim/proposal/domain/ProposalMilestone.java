package com.ibm.consulting.sim.proposal.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

@Embeddable
public class ProposalMilestone {
    @Column(name = "phase", columnDefinition = "text")
    private String phase;
    @Column(name = "duration", columnDefinition = "text")
    private String duration;

    protected ProposalMilestone() {}
    public ProposalMilestone(String phase, String duration) { this.phase = phase; this.duration = duration; }
    public String getPhase() { return phase; }
    public String getDuration() { return duration; }
}

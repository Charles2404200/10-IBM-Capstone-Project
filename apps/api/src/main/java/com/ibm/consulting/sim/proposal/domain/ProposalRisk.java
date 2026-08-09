package com.ibm.consulting.sim.proposal.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

@Embeddable
public class ProposalRisk {
    @Column(name = "risk", columnDefinition = "text")
    private String risk;
    @Column(name = "severity", length = 20)
    private String severity;
    @Column(name = "mitigation", columnDefinition = "text")
    private String mitigation;

    protected ProposalRisk() {}
    public ProposalRisk(String risk, String severity, String mitigation) {
        this.risk = text(risk);
        this.severity = text(severity);
        this.mitigation = text(mitigation);
    }
    private static String text(String value) { return value == null ? "" : value.trim(); }
    public String getRisk() { return risk; }
    public String getSeverity() { return severity; }
    public String getMitigation() { return mitigation; }
}

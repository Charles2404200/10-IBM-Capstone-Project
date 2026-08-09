package com.ibm.consulting.sim.proposal.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

@Embeddable
public class ProposalEvidenceLink {
    @Column(name = "section", length = 60)
    private String section;
    @Column(name = "source_id", length = 100)
    private String sourceId;

    protected ProposalEvidenceLink() {}
    public ProposalEvidenceLink(String section, String sourceId) { this.section = section; this.sourceId = sourceId; }
    public String getSection() { return section; }
    public String getSourceId() { return sourceId; }
}

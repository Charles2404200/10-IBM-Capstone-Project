package com.ibm.consulting.sim.lead.domain;

import jakarta.persistence.*;
import java.util.UUID;

/** A visible signal on a lead card (e.g. "Recent funding", "Hiring aggressively"). */
@Entity
@Table(name = "lead_signals")
public class LeadSignal {

    @Id
    private UUID id = UUID.randomUUID();

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "lead_id", nullable = false)
    private Lead lead;

    @Column(nullable = false, columnDefinition = "text")
    private String label;

    @Column(nullable = false, columnDefinition = "text")
    private String category;

    protected LeadSignal() {}

    public static LeadSignal create(Lead lead, String label, String category) {
        LeadSignal s = new LeadSignal();
        s.lead = lead;
        s.label = label;
        s.category = category;
        return s;
    }

    public UUID getId() { return id; }
    public String getLabel() { return label; }
    public String getCategory() { return category; }
}

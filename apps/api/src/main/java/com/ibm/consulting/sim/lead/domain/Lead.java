package com.ibm.consulting.sim.lead.domain;

import com.ibm.consulting.sim.shared.domain.BaseEntity;
import jakarta.persistence.*;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "leads")
public class Lead extends BaseEntity {

    @Column(nullable = false)
    private UUID scenarioId;

    @Column(nullable = false)
    private String companyName;

    @Column(nullable = false)
    private String industry;

    @Column(columnDefinition = "text")
    private String publicDescription;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private LeadDifficulty difficulty;

    /** Visible signals exposed on the lead card */
    @OneToMany(mappedBy = "lead", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<LeadSignal> signals = new ArrayList<>();

    // ─── Hidden intelligence — never returned by LeadSummary; only exposed
    // progressively through LeadIntelligencePolicy as the learner researches. ───

    @Column(name = "potential_value_range", length = 100)
    private String potentialValueRange;

    @Column(name = "decision_maker", length = 150)
    private String decisionMaker;

    @Column(name = "technology_stack", length = 200)
    private String technologyStack;

    @Column(name = "budget_signal", length = 150)
    private String budgetSignal;

    @Column(name = "pain_severity", length = 100)
    private String painSeverity;

    protected Lead() {}

    public static Lead create(UUID scenarioId, String companyName, String industry,
                              String publicDescription, LeadDifficulty difficulty) {
        Lead l = new Lead();
        l.scenarioId = scenarioId;
        l.companyName = companyName;
        l.industry = industry;
        l.publicDescription = publicDescription;
        l.difficulty = difficulty;
        return l;
    }

    public UUID getScenarioId() { return scenarioId; }
    public String getCompanyName() { return companyName; }
    public String getIndustry() { return industry; }
    public String getPublicDescription() { return publicDescription; }
    public LeadDifficulty getDifficulty() { return difficulty; }
    public List<LeadSignal> getSignals() { return Collections.unmodifiableList(signals); }
    public String getPotentialValueRange() { return potentialValueRange; }
    public String getDecisionMaker() { return decisionMaker; }
    public String getTechnologyStack() { return technologyStack; }
    public String getBudgetSignal() { return budgetSignal; }
    public String getPainSeverity() { return painSeverity; }
}

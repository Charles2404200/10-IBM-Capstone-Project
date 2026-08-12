package com.ibm.consulting.sim.lead.domain;

import com.ibm.consulting.sim.shared.domain.BaseEntity;
import jakarta.persistence.*;
import org.hibernate.annotations.BatchSize;

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
    @BatchSize(size = 48)
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

    public static Lead copyForScenario(Lead source, UUID scenarioId) {
        Lead copy = create(scenarioId, source.companyName, source.industry, source.publicDescription, source.difficulty);
        copy.potentialValueRange = source.potentialValueRange;
        copy.decisionMaker = source.decisionMaker;
        copy.technologyStack = source.technologyStack;
        copy.budgetSignal = source.budgetSignal;
        copy.painSeverity = source.painSeverity;
        source.signals.forEach(signal -> copy.addSignal(signal.getLabel(), signal.getCategory()));
        return copy;
    }

    public void configure(String companyName, String industry, String publicDescription, LeadDifficulty difficulty,
                          String potentialValueRange, String decisionMaker, String technologyStack,
                          String budgetSignal, String painSeverity, List<SignalInput> configuredSignals) {
        this.companyName = required(companyName, "Company name");
        this.industry = required(industry, "Industry");
        this.publicDescription = publicDescription == null ? "" : publicDescription.trim();
        this.difficulty = difficulty == null ? LeadDifficulty.MEDIUM : difficulty;
        this.potentialValueRange = nullable(potentialValueRange);
        this.decisionMaker = nullable(decisionMaker);
        this.technologyStack = nullable(technologyStack);
        this.budgetSignal = nullable(budgetSignal);
        this.painSeverity = nullable(painSeverity);
        this.signals.clear();
        if (configuredSignals != null) configuredSignals.forEach(signal -> addSignal(signal.label(), signal.category()));
    }

    public void addSignal(String label, String category) {
        this.signals.add(LeadSignal.create(this, required(label, "Signal label"), required(category, "Signal category")));
    }

    public record SignalInput(String label, String category) {}

    private static String required(String value, String field) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(field + " is required");
        return value.trim();
    }

    private static String nullable(String value) { return value == null || value.isBlank() ? null : value.trim(); }

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

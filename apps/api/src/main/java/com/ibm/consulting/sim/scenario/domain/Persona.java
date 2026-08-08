package com.ibm.consulting.sim.scenario.domain;

import com.ibm.consulting.sim.shared.domain.BaseEntity;
import jakarta.persistence.*;

@Entity
@Table(name = "personas")
public class Persona extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "scenario_id", nullable = false)
    private Scenario scenario;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private String jobTitle;

    @Column(nullable = false)
    private String organisation;

    @Column(columnDefinition = "text")
    private String communicationStyle;

    @Column(columnDefinition = "text")
    private String visibleConcerns;

    /** Never exposed to the learner-facing API. */
    @Column(columnDefinition = "text")
    private String hiddenConcerns;

    @Column(columnDefinition = "text")
    private String businessGoals;

    @Column(nullable = false)
    private int promptVersion;

    protected Persona() {}

    public static Persona create(Scenario scenario, String name, String jobTitle, String organisation,
                                 String communicationStyle, String visibleConcerns,
                                 String hiddenConcerns, String businessGoals) {
        Persona p = new Persona();
        p.scenario = scenario;
        p.name = name;
        p.jobTitle = jobTitle;
        p.organisation = organisation;
        p.communicationStyle = communicationStyle;
        p.visibleConcerns = visibleConcerns;
        p.hiddenConcerns = hiddenConcerns;
        p.businessGoals = businessGoals;
        p.promptVersion = 1;
        return p;
    }

    public Scenario getScenario() { return scenario; }
    public String getName() { return name; }
    public String getJobTitle() { return jobTitle; }
    public String getOrganisation() { return organisation; }
    public String getCommunicationStyle() { return communicationStyle; }
    public String getVisibleConcerns() { return visibleConcerns; }
    public String getHiddenConcerns() { return hiddenConcerns; }
    public String getBusinessGoals() { return businessGoals; }
    public int getPromptVersion() { return promptVersion; }
}

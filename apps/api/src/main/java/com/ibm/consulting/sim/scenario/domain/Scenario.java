package com.ibm.consulting.sim.scenario.domain;

import com.ibm.consulting.sim.shared.domain.BaseEntity;
import com.ibm.consulting.sim.shared.domain.DomainException;
import jakarta.persistence.*;
import org.hibernate.annotations.BatchSize;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "scenarios")
public class Scenario extends BaseEntity {

    private static final String DEFAULT_ROLE = "Management Consultant";
    private static final String CRITERIA_DELIMITER = "|";

    @Column(nullable = false)
    private String title;

    @Column(nullable = false)
    private String industry;

    @Column(columnDefinition = "text")
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ScenarioStatus status;

    @Column(nullable = false)
    private int difficulty; // 1–5, overall/summary difficulty shown as stars

    /** Difficulty broken into named dimensions (1–5 each) so learners understand *why* a scenario is hard. */
    @Column(name = "information_ambiguity", nullable = false)
    private int informationAmbiguity = 3;

    @Column(name = "stakeholder_complexity", nullable = false)
    private int stakeholderComplexity = 3;

    @Column(name = "commercial_pressure", nullable = false)
    private int commercialPressure = 3;

    /** JSON configuration owned by DifficultyProfileService; null means use versioned defaults. */
    @Column(name = "difficulty_profile_config", columnDefinition = "text")
    private String difficultyProfileConfig;

    /** Pre-engagement briefing content, shown before the learner enters the Lead Pipeline. */
    @Column(name = "consultant_role", nullable = false)
    private String consultantRole = DEFAULT_ROLE;

    @Column(columnDefinition = "text", nullable = false)
    private String objective = "";

    /** Pipe-delimited list of success criteria bullet points — see {@link #getSuccessCriteria()}. */
    @Column(name = "success_criteria", columnDefinition = "text", nullable = false)
    private String successCriteria = "";

    @Column(name = "simulated_days", nullable = false)
    private int simulatedDays = 10;

    @Column(name = "content_version", nullable = false)
    private int contentVersion;

    /** Stable product identity shared by immutable scenario revisions. */
    @Column(name = "scenario_lineage_id", nullable = false, updatable = false)
    private UUID scenarioLineageId;

    /** JSON owned by ScenarioAuthoringConfigService; null retains seeded-scenario defaults. */
    @Column(name = "authoring_config", columnDefinition = "text")
    private String authoringConfig;

    @Column(name = "rubric_weights")
    private String rubricWeightsEncoded;

    @OneToMany(mappedBy = "scenario", cascade = CascadeType.ALL, orphanRemoval = true)
    @BatchSize(size = 24)
    private List<Persona> personas = new ArrayList<>();

    protected Scenario() {}

    public static Scenario create(String title, String industry, String description, int difficulty) {
        Scenario s = new Scenario();
        s.title = title;
        s.industry = industry;
        s.description = description;
        s.difficulty = difficulty;
        s.status = ScenarioStatus.DRAFT;
        s.contentVersion = 1;
        s.scenarioLineageId = s.getId();
        return s;
    }

    public void publish() {
        if (status != ScenarioStatus.DRAFT) throw new ScenarioNotEditableException(status);
        this.status = ScenarioStatus.ACTIVE;
    }
    public void archive() { this.status = ScenarioStatus.ARCHIVED; }

    /** Creates a new DRAFT revision while preserving the published revision for active engagements. */
    public Scenario createRevision() {
        Scenario revision = new Scenario();
        revision.title = title;
        revision.industry = industry;
        revision.description = description;
        revision.difficulty = difficulty;
        revision.informationAmbiguity = informationAmbiguity;
        revision.stakeholderComplexity = stakeholderComplexity;
        revision.commercialPressure = commercialPressure;
        revision.difficultyProfileConfig = difficultyProfileConfig;
        revision.consultantRole = consultantRole;
        revision.objective = objective;
        revision.successCriteria = successCriteria;
        revision.simulatedDays = simulatedDays;
        revision.rubricWeightsEncoded = rubricWeightsEncoded;
        revision.authoringConfig = authoringConfig;
        revision.scenarioLineageId = scenarioLineageId;
        revision.contentVersion = contentVersion + 1;
        revision.status = ScenarioStatus.DRAFT;
        return revision;
    }

    public void updateMetadata(String title, String industry, String description, int difficulty) {
        assertDraftEditable();
        this.title = required(title, "Title");
        this.industry = required(industry, "Industry");
        this.description = required(description, "Description");
        this.difficulty = Math.max(1, Math.min(5, difficulty));
    }

    /** Author/admin capability: configure the pre-engagement briefing shown to learners. */
    public void updateBriefing(String consultantRole, String objective, List<String> successCriteria, int simulatedDays) {
        assertDraftEditable();
        this.consultantRole = (consultantRole == null || consultantRole.isBlank()) ? DEFAULT_ROLE : consultantRole;
        this.objective = objective == null ? "" : objective;
        this.successCriteria = successCriteria == null ? "" : String.join(CRITERIA_DELIMITER, successCriteria);
        this.simulatedDays = simulatedDays > 0 ? simulatedDays : 10;
    }

    /** Author/admin capability: configure how the difficulty is broken down for learners. */
    public void updateDifficultyDimensions(int informationAmbiguity, int stakeholderComplexity, int commercialPressure) {
        assertDraftEditable();
        this.informationAmbiguity = clampDimension(informationAmbiguity);
        this.stakeholderComplexity = clampDimension(stakeholderComplexity);
        this.commercialPressure = clampDimension(commercialPressure);
    }

    public void updateDifficultyProfileConfig(String difficultyProfileConfig) {
        assertDraftEditable();
        this.difficultyProfileConfig = difficultyProfileConfig;
    }

    public void updateAuthoringConfig(String authoringConfig) {
        assertDraftEditable();
        this.authoringConfig = authoringConfig;
    }

    private int clampDimension(int value) {
        return Math.max(1, Math.min(5, value));
    }

    public Persona addPersona(String name, String jobTitle, String organisation, String communicationStyle,
                               String visibleConcerns, String hiddenConcerns, String businessGoals) {
        assertDraftEditable();
        Persona persona = Persona.create(this, name, jobTitle, organisation, communicationStyle,
                visibleConcerns, hiddenConcerns, businessGoals);
        this.personas.add(persona);
        return persona;
    }

    /**
     * Replaces the scenario's competency weighting used by {@code AssessmentEngine}
     * when computing the overall score. Weights must sum to 100; an empty map
     * clears any customisation and restores the equal-weight default.
     */
    public void updateRubricWeights(Map<String, Integer> weights) {
        assertDraftEditable();
        if (weights != null && !weights.isEmpty()) {
            int total = weights.values().stream().mapToInt(Integer::intValue).sum();
            if (total != 100) {
                throw new InvalidRubricWeightsException(total);
            }
        }
        this.rubricWeightsEncoded = RubricWeightCodec.encode(weights);
    }

    public Map<String, Integer> getRubricWeights() {
        return RubricWeightCodec.decode(rubricWeightsEncoded);
    }

    public String getTitle() { return title; }
    public String getIndustry() { return industry; }
    public String getDescription() { return description; }
    public ScenarioStatus getStatus() { return status; }
    public int getDifficulty() { return difficulty; }
    public int getInformationAmbiguity() { return informationAmbiguity; }
    public int getStakeholderComplexity() { return stakeholderComplexity; }
    public int getCommercialPressure() { return commercialPressure; }
    public String getDifficultyProfileConfig() { return difficultyProfileConfig; }
    public String getConsultantRole() { return consultantRole; }
    public String getObjective() { return objective; }
    public List<String> getSuccessCriteria() {
        if (successCriteria == null || successCriteria.isBlank()) return List.of();
        return Arrays.stream(successCriteria.split("\\" + CRITERIA_DELIMITER)).map(String::strip).toList();
    }
    public int getSimulatedDays() { return simulatedDays; }
    public int getContentVersion() { return contentVersion; }
    public UUID getScenarioLineageId() { return scenarioLineageId; }
    public String getAuthoringConfig() { return authoringConfig; }
    public List<Persona> getPersonas() { return Collections.unmodifiableList(personas); }

    private void assertDraftEditable() {
        if (status != ScenarioStatus.DRAFT) throw new ScenarioNotEditableException(status);
    }

    private String required(String value, String field) {
        if (value == null || value.isBlank()) throw new InvalidScenarioAuthoringConfigException(field + " is required");
        return value.trim();
    }

    public static class ScenarioNotEditableException extends DomainException {
        public ScenarioNotEditableException(ScenarioStatus status) {
            super("Scenario is " + status + ". Create a new revision before changing authored content.");
        }
    }

    public static class InvalidRubricWeightsException extends DomainException {
        public InvalidRubricWeightsException(int total) {
            super("Rubric weights must sum to 100, got: " + total);
        }
    }
}

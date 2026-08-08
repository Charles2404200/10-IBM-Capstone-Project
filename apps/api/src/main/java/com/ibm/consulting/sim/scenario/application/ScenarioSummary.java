package com.ibm.consulting.sim.scenario.application;

import com.ibm.consulting.sim.scenario.domain.Persona;
import com.ibm.consulting.sim.scenario.domain.Scenario;

import java.util.List;
import java.util.Map;
import java.util.UUID;

public record ScenarioSummary(
        UUID id,
        String title,
        String industry,
        String description,
        int difficulty,
        int contentVersion,
        String status,
        List<PersonaSummary> personas,
        Map<String, Integer> rubricWeights,
        DifficultyProfile difficultyProfile,
        Briefing briefing) {

    /** Named difficulty dimensions — explains *why* a scenario is hard, not just a single 1–5 number. */
    public record DifficultyProfile(int informationAmbiguity, int stakeholderComplexity, int commercialPressure) {}

    /** Pre-engagement briefing content shown before the learner enters the Lead Pipeline. */
    public record Briefing(String consultantRole, String objective, List<String> successCriteria, int simulatedDays) {}

    /**
     * Learner-facing view of a persona. Deliberately excludes {@code hiddenConcerns},
     * which must never be exposed to the client — it drives AI grounding only.
     */
    public record PersonaSummary(
            UUID id,
            String name,
            String jobTitle,
            String organisation,
            String communicationStyle,
            String visibleConcerns) {

        static PersonaSummary from(Persona p) {
            return new PersonaSummary(p.getId(), p.getName(), p.getJobTitle(), p.getOrganisation(),
                    p.getCommunicationStyle(), p.getVisibleConcerns());
        }
    }

    public static ScenarioSummary from(Scenario s) {
        return new ScenarioSummary(s.getId(), s.getTitle(), s.getIndustry(),
                s.getDescription(), s.getDifficulty(), s.getContentVersion(), s.getStatus().name(),
                s.getPersonas().stream().map(PersonaSummary::from).toList(), s.getRubricWeights(),
                new DifficultyProfile(s.getInformationAmbiguity(), s.getStakeholderComplexity(), s.getCommercialPressure()),
                new Briefing(s.getConsultantRole(), s.getObjective(), s.getSuccessCriteria(), s.getSimulatedDays()));
    }
}

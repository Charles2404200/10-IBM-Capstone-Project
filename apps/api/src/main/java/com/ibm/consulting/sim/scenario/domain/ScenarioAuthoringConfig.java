package com.ibm.consulting.sim.scenario.domain;

import com.ibm.consulting.sim.lead.domain.EvidenceType;

import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Structured, versioned truth and reveal configuration owned by a scenario revision. */
public record ScenarioAuthoringConfig(List<CanonicalFact> canonicalFacts, List<RevealRule> revealRules) {
    public ScenarioAuthoringConfig {
        canonicalFacts = canonicalFacts == null ? List.of() : List.copyOf(canonicalFacts);
        revealRules = revealRules == null ? List.of() : List.copyOf(revealRules);
        Set<String> ids = new HashSet<>();
        for (CanonicalFact fact : canonicalFacts) {
            if (!ids.add(fact.id())) throw new InvalidScenarioAuthoringConfigException("Canonical fact ids must be unique");
        }
        Set<RevealTarget> targets = new HashSet<>();
        for (RevealRule rule : revealRules) {
            if (!targets.add(rule.target())) throw new InvalidScenarioAuthoringConfigException("Reveal targets must be unique");
        }
    }

    public static ScenarioAuthoringConfig empty() { return new ScenarioAuthoringConfig(List.of(), List.of()); }

    /** Legacy-safe reveal defaults used for seeded scenarios without an authoring config. */
    public static ScenarioAuthoringConfig defaults() {
        return new ScenarioAuthoringConfig(List.of(), List.of(
                new RevealRule(RevealTarget.DECISION_MAKER, Set.of(EvidenceType.STAKEHOLDER_PROFILE), 1),
                new RevealRule(RevealTarget.PAIN_SEVERITY, Set.of(EvidenceType.COMPANY_NEWS, EvidenceType.MARKET_TREND), 1),
                new RevealRule(RevealTarget.TECHNOLOGY_STACK, Set.of(EvidenceType.TECHNOLOGY_INDICATOR), 1),
                new RevealRule(RevealTarget.BUDGET_SIGNAL, Set.of(EvidenceType.FINANCIAL_SIGNAL), 1),
                new RevealRule(RevealTarget.POTENTIAL_VALUE,
                        Set.of(EvidenceType.STAKEHOLDER_PROFILE, EvidenceType.FINANCIAL_SIGNAL), 2)));
    }

    public RevealRule ruleFor(RevealTarget target) {
        return revealRules.stream().filter(rule -> rule.target() == target).findFirst()
                .orElseGet(() -> defaults().revealRules.stream()
                        .filter(rule -> rule.target() == target).findFirst().orElseThrow());
    }
}

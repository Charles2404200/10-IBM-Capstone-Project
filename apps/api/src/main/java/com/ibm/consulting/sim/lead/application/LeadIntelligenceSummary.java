package com.ibm.consulting.sim.lead.application;

import com.ibm.consulting.sim.lead.domain.Lead;
import com.ibm.consulting.sim.lead.domain.LeadIntelligencePolicy;
import com.ibm.consulting.sim.lead.domain.LeadIntelligencePolicy.Insight;
import com.ibm.consulting.sim.lead.domain.ResearchEvidence;
import com.ibm.consulting.sim.lead.domain.EvidenceType;
import com.ibm.consulting.sim.scenario.domain.RevealRule;
import com.ibm.consulting.sim.scenario.domain.RevealTarget;
import com.ibm.consulting.sim.scenario.domain.ScenarioAuthoringConfig;

import java.util.List;
import java.util.UUID;

/**
 * The learner-facing "Client Profile" view for the selected lead: each field
 * carries both the revealed value — derived from the learner's own evidence
 * content, not a hidden scenario preset (see {@link LeadIntelligencePolicy}) —
 * and which evidence items earned that reveal, so the panel can explain
 * itself ("Based on E-02") rather than presenting facts with no traceable
 * origin. Unrevealed fields have a {@code null} value and the frontend
 * renders them as "Unknown — keep researching".
 */
public record LeadIntelligenceSummary(
        UUID leadId,
        String companyName,
        String industry,
        long evidenceCount,
        String confidenceLabel,
        int confidenceScore,
        List<String> confidenceFactors,
        Field potentialValueRange,
        Field decisionMaker,
        Field technologyStack,
        Field budgetSignal,
        Field painSeverity) {

    /** A single revealed (or not-yet-revealed) intelligence field: the value plus
     *  the evidence sequence numbers ("E-01", "E-03", ...) that earned it. */
    public record Field(String value, List<Integer> supportingEvidence) {
        static Field from(Insight insight) {
            return new Field(insight.value(), insight.supportingEvidenceSequence());
        }
    }

    public static LeadIntelligenceSummary from(Lead lead, List<ResearchEvidence> evidence) {
        return from(lead, evidence, ScenarioAuthoringConfig.defaults());
    }

    /** Applies author-owned reveal rules; facts remain derived from learner evidence. */
    public static LeadIntelligenceSummary from(Lead lead, List<ResearchEvidence> evidence,
                                              ScenarioAuthoringConfig config) {
        return new LeadIntelligenceSummary(
                lead.getId(), lead.getCompanyName(), lead.getIndustry(), evidence.size(),
                LeadIntelligencePolicy.confidenceLabel(evidence),
                LeadIntelligencePolicy.confidenceScore(evidence),
                LeadIntelligencePolicy.confidenceFactors(evidence),
                Field.from(gate(LeadIntelligencePolicy.potentialValue(evidence, lead.getPotentialValueRange()), evidence,
                        config.ruleFor(RevealTarget.POTENTIAL_VALUE))),
                Field.from(gate(LeadIntelligencePolicy.decisionMaker(evidence), evidence,
                        config.ruleFor(RevealTarget.DECISION_MAKER))),
                Field.from(gate(LeadIntelligencePolicy.technologyStack(evidence), evidence,
                        config.ruleFor(RevealTarget.TECHNOLOGY_STACK))),
                Field.from(gate(LeadIntelligencePolicy.budgetSignal(evidence), evidence,
                        config.ruleFor(RevealTarget.BUDGET_SIGNAL))),
                Field.from(gate(LeadIntelligencePolicy.painSeverity(evidence), evidence,
                        config.ruleFor(RevealTarget.PAIN_SEVERITY))));
    }

    private static Insight gate(Insight insight, List<ResearchEvidence> evidence, RevealRule rule) {
        boolean everyTypeCovered = rule.requiredEvidenceTypes().stream()
                .allMatch(type -> evidence.stream().anyMatch(item -> item.getEvidenceType() == type));
        long matchingEvidence = evidence.stream()
                .filter(item -> rule.requiredEvidenceTypes().contains(item.getEvidenceType())).count();
        return everyTypeCovered && matchingEvidence >= rule.minimumEvidenceCount()
                ? insight : new Insight(null, List.of());
    }
}

package com.ibm.consulting.sim.lead.application;

import com.ibm.consulting.sim.lead.domain.Lead;
import com.ibm.consulting.sim.lead.domain.LeadIntelligencePolicy;
import com.ibm.consulting.sim.lead.domain.LeadIntelligencePolicy.Insight;
import com.ibm.consulting.sim.lead.domain.ResearchEvidence;

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
        return new LeadIntelligenceSummary(
                lead.getId(), lead.getCompanyName(), lead.getIndustry(), evidence.size(),
                LeadIntelligencePolicy.confidenceLabel(evidence),
                LeadIntelligencePolicy.confidenceScore(evidence),
                LeadIntelligencePolicy.confidenceFactors(evidence),
                Field.from(LeadIntelligencePolicy.potentialValue(evidence, lead.getPotentialValueRange())),
                Field.from(LeadIntelligencePolicy.decisionMaker(evidence)),
                Field.from(LeadIntelligencePolicy.technologyStack(evidence)),
                Field.from(LeadIntelligencePolicy.budgetSignal(evidence)),
                Field.from(LeadIntelligencePolicy.painSeverity(evidence)));
    }
}

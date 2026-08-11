package com.ibm.consulting.sim.scenario.application;

import com.ibm.consulting.sim.lead.domain.Lead;

import java.util.List;
import java.util.UUID;

/** Author-only lead representation. It intentionally includes canonical fields omitted from learner APIs. */
public record LeadAuthoringView(
        UUID id,
        String companyName,
        String industry,
        String publicDescription,
        String difficulty,
        String potentialValueRange,
        String decisionMaker,
        String technologyStack,
        String budgetSignal,
        String painSeverity,
        List<LeadAuthoringRequest.Signal> signals) {

    public static LeadAuthoringView from(Lead lead) {
        return new LeadAuthoringView(
                lead.getId(), lead.getCompanyName(), lead.getIndustry(), lead.getPublicDescription(),
                lead.getDifficulty().name(), lead.getPotentialValueRange(), lead.getDecisionMaker(),
                lead.getTechnologyStack(), lead.getBudgetSignal(), lead.getPainSeverity(),
                lead.getSignals().stream()
                        .map(signal -> new LeadAuthoringRequest.Signal(signal.getLabel(), signal.getCategory()))
                        .toList());
    }
}

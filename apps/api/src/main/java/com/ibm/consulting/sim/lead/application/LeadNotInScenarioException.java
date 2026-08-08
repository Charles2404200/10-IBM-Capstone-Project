package com.ibm.consulting.sim.lead.application;

import com.ibm.consulting.sim.shared.domain.DomainException;

import java.util.UUID;

public class LeadNotInScenarioException extends DomainException {
    public LeadNotInScenarioException(UUID leadId, UUID scenarioId) {
        super("Lead %s does not belong to scenario %s".formatted(leadId, scenarioId));
    }
}

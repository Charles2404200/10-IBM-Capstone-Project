package com.ibm.consulting.sim.lead.application;

import com.ibm.consulting.sim.shared.domain.DomainException;

import java.util.UUID;

/** Raised when lead-scoped work is requested before an engagement selects its lead. */
public class LeadNotSelectedException extends DomainException {

    public LeadNotSelectedException(UUID engagementId) {
        super("No lead has been selected for engagement " + engagementId);
    }
}

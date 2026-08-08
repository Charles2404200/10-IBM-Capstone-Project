package com.ibm.consulting.sim.lead.application;

import com.ibm.consulting.sim.shared.domain.DomainException;

import java.util.UUID;

/**
 * Thrown when a learner attempts to select a lead for an engagement that has
 * already progressed past lead selection (a different lead is already locked
 * in, or research/outreach/etc. has already started). Gives a precise,
 * actionable message instead of surfacing the generic state-machine
 * {@code InvalidTransitionException}.
 */
public class LeadAlreadySelectedException extends DomainException {
    public LeadAlreadySelectedException(UUID engagementId) {
        super("Engagement %s has already selected a lead — it cannot be changed once research has begun."
                .formatted(engagementId));
    }
}

package com.ibm.consulting.sim.engagement.domain;

import com.ibm.consulting.sim.shared.domain.DomainException;

public class InvalidTransitionException extends DomainException {

    public InvalidTransitionException(EngagementState from, EngagementState to) {
        super("Invalid engagement transition: %s → %s".formatted(from, to));
    }
}

package com.ibm.consulting.sim.engagement.domain;

import com.ibm.consulting.sim.shared.domain.DomainException;

public class LifecycleGateNotSatisfiedException extends DomainException {
    public LifecycleGateNotSatisfiedException(String message) { super(message); }
}

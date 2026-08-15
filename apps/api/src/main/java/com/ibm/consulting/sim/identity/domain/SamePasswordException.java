package com.ibm.consulting.sim.identity.domain;

import com.ibm.consulting.sim.shared.domain.DomainException;

public class SamePasswordException extends DomainException {
    public SamePasswordException() {
        super("Same Password cannot be used again");
    }
}

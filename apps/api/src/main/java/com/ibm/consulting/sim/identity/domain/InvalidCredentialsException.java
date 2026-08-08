package com.ibm.consulting.sim.identity.domain;

import com.ibm.consulting.sim.shared.domain.DomainException;

public class InvalidCredentialsException extends DomainException {
    public InvalidCredentialsException() {
        super("Invalid email or password");
    }
}

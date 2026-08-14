package com.ibm.consulting.sim.identity.domain;

import com.ibm.consulting.sim.shared.domain.DomainException;

public class InvalidPasswordResetTokenException extends DomainException {
    public InvalidPasswordResetTokenException() {
        super("Invalid token");
    }
}
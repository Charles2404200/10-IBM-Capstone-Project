package com.ibm.consulting.sim.identity.domain;

import com.ibm.consulting.sim.shared.domain.DomainException;

public class PasswordMismatchException extends DomainException {
    public PasswordMismatchException() {
        super("The password did not match");
    }
}

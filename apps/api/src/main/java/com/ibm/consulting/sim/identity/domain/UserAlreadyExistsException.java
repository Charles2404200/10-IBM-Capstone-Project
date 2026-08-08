package com.ibm.consulting.sim.identity.domain;

import com.ibm.consulting.sim.shared.domain.DomainException;

public class UserAlreadyExistsException extends DomainException {
    public UserAlreadyExistsException(String email) {
        super("User already exists with email: " + email);
    }
}

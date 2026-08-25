package com.ibm.consulting.sim.identity.domain;

import com.ibm.consulting.sim.shared.domain.DomainException;

public class InvalidCredentialTokenException extends DomainException {
    public InvalidCredentialTokenException(String credentialType) {
        super("This " + credentialType + " link is invalid or has expired.");
    }
}

package com.ibm.consulting.sim.identity.domain;

import com.ibm.consulting.sim.shared.domain.DomainException;

public class EmailVerificationRequiredException extends DomainException {
    public EmailVerificationRequiredException() {
        super("Verify your email address before signing in.");
    }
}

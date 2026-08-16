package com.ibm.consulting.sim.identity.domain;

import com.ibm.consulting.sim.shared.domain.DomainException;

public class InvalidOtpException extends DomainException {
    public InvalidOtpException() {
        super("Invalid or expired OTP");
    }
}

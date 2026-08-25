package com.ibm.consulting.sim.shared.email.application;

import com.ibm.consulting.sim.shared.domain.DomainException;

public class EmailDeliveryUnavailableException extends DomainException {
    public EmailDeliveryUnavailableException(String message) { super(message); }
    public EmailDeliveryUnavailableException(String message, Throwable cause) { super(message, cause); }
}

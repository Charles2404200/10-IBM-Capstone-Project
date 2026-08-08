package com.ibm.consulting.sim.shared.domain;

/** Marker interface for domain exceptions that should map to client-visible HTTP errors. */
public abstract class DomainException extends RuntimeException {

    protected DomainException(String message) {
        super(message);
    }

    protected DomainException(String message, Throwable cause) {
        super(message, cause);
    }
}

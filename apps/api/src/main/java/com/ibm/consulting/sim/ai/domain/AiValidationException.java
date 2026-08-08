package com.ibm.consulting.sim.ai.domain;

public class AiValidationException extends RuntimeException {
    public AiValidationException(String message) {
        super(message);
    }

    public AiValidationException(String message, Throwable cause) {
        super(message, cause);
    }
}

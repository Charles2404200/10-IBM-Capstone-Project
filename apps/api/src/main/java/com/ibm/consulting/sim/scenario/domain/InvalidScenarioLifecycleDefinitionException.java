package com.ibm.consulting.sim.scenario.domain;

import com.ibm.consulting.sim.shared.domain.DomainException;

public class InvalidScenarioLifecycleDefinitionException extends DomainException {
    public InvalidScenarioLifecycleDefinitionException(String message) {
        super(message);
    }

    public InvalidScenarioLifecycleDefinitionException(String message, Throwable cause) {
        super(message, cause);
    }
}

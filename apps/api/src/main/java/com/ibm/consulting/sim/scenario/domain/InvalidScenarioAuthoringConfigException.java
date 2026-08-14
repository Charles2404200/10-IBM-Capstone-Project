package com.ibm.consulting.sim.scenario.domain;

import com.ibm.consulting.sim.shared.domain.DomainException;

public class InvalidScenarioAuthoringConfigException extends DomainException {
    public InvalidScenarioAuthoringConfigException(String message) { super(message); }
    public InvalidScenarioAuthoringConfigException(String message, Throwable cause) { super(message, cause); }
}

package com.ibm.consulting.sim.scenario.application;

import com.ibm.consulting.sim.shared.domain.DomainException;

public class ScenarioLifecycleConflictException extends DomainException {
    public ScenarioLifecycleConflictException() {
        super("This draft changed after you opened it. Reload the latest lifecycle before saving your changes.");
    }
}

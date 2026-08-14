package com.ibm.consulting.sim.scenario.application;

import com.ibm.consulting.sim.shared.domain.DomainException;

import java.util.List;

/** Publish is blocked until a playable scenario revision has the required content. */
public class ScenarioNotReadyException extends DomainException {
    private final List<String> blockers;

    public ScenarioNotReadyException(List<String> blockers) {
        super("Scenario is not ready to publish: " + String.join(" ", blockers));
        this.blockers = List.copyOf(blockers);
    }

    public List<String> getBlockers() { return blockers; }
}

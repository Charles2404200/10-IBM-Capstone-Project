package com.ibm.consulting.sim.scenario.application;

import com.ibm.consulting.sim.scenario.domain.ScenarioLifecycleDefinition;

/** Version is the optimistic-lock token, independent of the content revision number. */
public record ScenarioLifecycleView(ScenarioLifecycleDefinition definition, Long version) {}

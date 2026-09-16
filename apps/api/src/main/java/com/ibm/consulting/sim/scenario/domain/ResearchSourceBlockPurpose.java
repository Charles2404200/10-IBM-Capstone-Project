package com.ibm.consulting.sim.scenario.domain;

/**
 * The epistemic role of a document fragment. Only factual statements and
 * explicitly grounded interpretations can become learner evidence.
 */
public enum ResearchSourceBlockPurpose {
    FACT,
    INTERPRETATION,
    CONTEXT,
    UNCERTAINTY,
    GUIDANCE
}

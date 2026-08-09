package com.ibm.consulting.sim.outreach.domain;

/**
 * The learner action the client is currently waiting for. This is separate
 * from an outreach outcome: FOLLOW_UP_REQUIRED explains the response, while
 * the next action tells the product which workspace must open next.
 */
public enum OutreachNextAction {
    NONE,
    SEND_FOLLOW_UP,
    SUBMIT_CAPABILITY_BRIEF,
    CONTINUE_TO_MEETING
}

package com.ibm.consulting.sim.shared.application.outbox;

/** Result of completing a failed Kafka publication attempt. */
public enum OutboxFailureOutcome {
    RETRY_SCHEDULED,
    TERMINALLY_FAILED,
    OWNERSHIP_LOST
}

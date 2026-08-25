package com.ibm.consulting.sim.shared.email.application;

import java.util.Objects;

/**
 * Published inside the owning business transaction and delivered only after it
 * commits. This keeps SMTP latency and provider outages off authentication
 * request paths without sending credentials for a transaction that rolled back.
 */
public record TransactionalEmailRequestedEvent(OutboundEmail email) {
    public TransactionalEmailRequestedEvent {
        Objects.requireNonNull(email, "email is required");
    }
}

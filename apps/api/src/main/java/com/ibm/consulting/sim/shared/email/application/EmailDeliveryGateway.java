package com.ibm.consulting.sim.shared.email.application;

/** Boundary separating product workflows from a transactional-email provider. */
public interface EmailDeliveryGateway {
    void send(OutboundEmail email);
}

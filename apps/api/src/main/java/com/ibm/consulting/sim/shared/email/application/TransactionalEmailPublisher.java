package com.ibm.consulting.sim.shared.email.application;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

import java.util.Objects;

/** Application boundary for requesting transactional email delivery. */
@Component
public class TransactionalEmailPublisher {

    private final ApplicationEventPublisher eventPublisher;

    public TransactionalEmailPublisher(ApplicationEventPublisher eventPublisher) {
        this.eventPublisher = eventPublisher;
    }

    public void publish(OutboundEmail email) {
        eventPublisher.publishEvent(new TransactionalEmailRequestedEvent(Objects.requireNonNull(email)));
    }
}

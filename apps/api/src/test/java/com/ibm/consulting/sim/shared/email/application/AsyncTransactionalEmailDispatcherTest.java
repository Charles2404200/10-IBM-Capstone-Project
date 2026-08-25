package com.ibm.consulting.sim.shared.email.application;

import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class AsyncTransactionalEmailDispatcherTest {

    private static final OutboundEmail EMAIL = new OutboundEmail(
            "learner@example.test", "Confirm your email", "<p>Confirm</p>", "Confirm");

    @Test
    void retriesTransientDeliveryFailuresWithoutRethrowingThemToTheRequestPath() {
        AtomicInteger attempts = new AtomicInteger();
        EmailDeliveryGateway gateway = email -> {
            if (attempts.incrementAndGet() < 3) {
                throw new IllegalStateException("transient SMTP failure");
            }
        };
        AsyncTransactionalEmailDispatcher dispatcher = new AsyncTransactionalEmailDispatcher(gateway, 3, 0);

        dispatcher.deliver(new TransactionalEmailRequestedEvent(EMAIL));

        assertThat(attempts).hasValue(3);
    }

    @Test
    void stopsAfterTheConfiguredMaximumAttempts() {
        AtomicInteger attempts = new AtomicInteger();
        EmailDeliveryGateway gateway = email -> {
            attempts.incrementAndGet();
            throw new IllegalStateException("SMTP unavailable");
        };
        AsyncTransactionalEmailDispatcher dispatcher = new AsyncTransactionalEmailDispatcher(gateway, 2, 0);

        dispatcher.deliver(new TransactionalEmailRequestedEvent(EMAIL));

        assertThat(attempts).hasValue(2);
    }
}

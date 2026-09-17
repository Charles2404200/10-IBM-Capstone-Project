package com.ibm.consulting.sim.shared.email.application;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import java.util.List;
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

    @Test
    void sanitisesProviderDetailsInWarningsAndTheTerminalError() {
        String privateAddress = "learner.private@example.com";
        EmailDeliveryGateway gateway = email -> {
            throw new IllegalStateException("SMTP delivery failed",
                    new IllegalArgumentException("550 rejected " + privateAddress));
        };
        AsyncTransactionalEmailDispatcher dispatcher = new AsyncTransactionalEmailDispatcher(gateway, 2, 0);
        Logger logger = (Logger) LoggerFactory.getLogger(AsyncTransactionalEmailDispatcher.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);

        try {
            dispatcher.deliver(new TransactionalEmailRequestedEvent(EMAIL));

            List<ILoggingEvent> warnings = appender.list.stream()
                    .filter(event -> event.getLevel() == Level.WARN)
                    .toList();
            assertThat(warnings).isNotEmpty();
            assertThat(warnings).allSatisfy(event ->
                    assertThat(event.getFormattedMessage())
                            .doesNotContain(privateAddress)
                            .contains("***@***"));

            ILoggingEvent terminalError = appender.list.stream()
                    .filter(event -> event.getLevel() == Level.ERROR)
                    .findFirst()
                    .orElseThrow();
            assertThat(terminalError.getFormattedMessage())
                    .doesNotContain(privateAddress)
                    .contains("***@***", "IllegalArgumentException");
            assertThat(terminalError.getThrowableProxy()).isNull();
        } finally {
            logger.detachAppender(appender);
            appender.stop();
        }
    }

    @Test
    void stopsRetryingWhenTheBackoffWaitIsInterrupted() {
        AtomicInteger attempts = new AtomicInteger();
        EmailDeliveryGateway gateway = email -> {
            attempts.incrementAndGet();
            throw new IllegalStateException("SMTP unavailable");
        };
        AsyncTransactionalEmailDispatcher dispatcher = new AsyncTransactionalEmailDispatcher(gateway, 3, 10);

        try {
            Thread.currentThread().interrupt();

            dispatcher.deliver(new TransactionalEmailRequestedEvent(EMAIL));

            assertThat(attempts).hasValue(1);
            assertThat(Thread.currentThread().isInterrupted()).isTrue();
        } finally {
            Thread.interrupted();
        }
    }
}

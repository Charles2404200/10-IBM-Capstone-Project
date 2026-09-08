package com.ibm.consulting.sim.identity.application;

import com.github.benmanes.caffeine.cache.Ticker;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LoginAttemptLimiterTest {

    private final MutableTicker ticker = new MutableTicker();
    private LoginAttemptLimiter limiter;

    @BeforeEach
    void setUp() {
        LoginAttemptProperties properties = new LoginAttemptProperties();
        properties.setMaxFailures(3);
        properties.setWindow(Duration.ofMinutes(2));
        properties.setMaximumTrackedAccounts(100);
        limiter = new LoginAttemptLimiter(properties, ticker);
    }

    @Test
    void blocksOnlyTheRepeatedlyFailingNormalisedAccount() {
        for (int attempt = 0; attempt < 3; attempt++) {
            limiter.checkAllowed(" User@Example.com ");
            limiter.recordFailure("user@example.com");
        }

        assertThatThrownBy(() -> limiter.checkAllowed("USER@example.com"))
                .isInstanceOf(LoginRateLimitExceededException.class);
        assertThatCode(() -> limiter.checkAllowed("another@example.com")).doesNotThrowAnyException();
    }

    @Test
    void successfulAuthenticationResetsFailures() {
        limiter.recordFailure("user@example.com");
        limiter.recordFailure("user@example.com");
        limiter.recordFailure("user@example.com");
        limiter.recordSuccess("user@example.com");

        assertThatCode(() -> limiter.checkAllowed("user@example.com")).doesNotThrowAnyException();
    }

    @Test
    void failuresExpireAfterTheConfiguredWindow() {
        limiter.recordFailure("user@example.com");
        limiter.recordFailure("user@example.com");
        limiter.recordFailure("user@example.com");
        ticker.advance(Duration.ofMinutes(2).plusNanos(1));

        assertThatCode(() -> limiter.checkAllowed("user@example.com")).doesNotThrowAnyException();
    }

    private static final class MutableTicker implements Ticker {
        private final AtomicLong nanos = new AtomicLong();

        @Override public long read() { return nanos.get(); }
        void advance(Duration duration) { nanos.addAndGet(duration.toNanos()); }
    }
}

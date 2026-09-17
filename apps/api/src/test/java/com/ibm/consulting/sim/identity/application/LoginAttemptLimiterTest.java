package com.ibm.consulting.sim.identity.application;

import com.github.benmanes.caffeine.cache.Ticker;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
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
            assertThatCode(() -> limiter.acquire(" User@Example.com ")).doesNotThrowAnyException();
        }

        assertThatThrownBy(() -> limiter.acquire("USER@example.com"))
                .isInstanceOf(LoginRateLimitExceededException.class);
        assertThatCode(() -> limiter.acquire("another@example.com")).doesNotThrowAnyException();
    }

    @Test
    void successfulAuthenticationResetsFailures() {
        limiter.acquire("user@example.com");
        limiter.acquire("user@example.com");
        limiter.acquire("user@example.com");
        limiter.recordSuccess("user@example.com");

        assertThatCode(() -> limiter.acquire("user@example.com")).doesNotThrowAnyException();
    }

    @Test
    void failuresExpireAfterTheConfiguredWindow() {
        limiter.acquire("user@example.com");
        limiter.acquire("user@example.com");
        limiter.acquire("user@example.com");
        ticker.advance(Duration.ofMinutes(2).plusNanos(1));

        assertThatCode(() -> limiter.acquire("user@example.com")).doesNotThrowAnyException();
    }

    @Test
    void distributedShadowReadsAndDenialsDoNotExtendTheWindow() {
        LoginAttemptProperties properties = new LoginAttemptProperties();
        properties.setMaxFailures(3);
        properties.setWindow(Duration.ofMinutes(2));
        properties.setMaximumTrackedAccounts(100);
        CaffeineLoginAttemptStore store = new CaffeineLoginAttemptStore(properties, ticker);
        store.synchronizeAtLeast("account-hash", 3, true);
        ticker.advance(Duration.ofMinutes(1).plusSeconds(59));

        store.synchronizeAtLeast("account-hash", 3, false);
        ticker.advance(Duration.ofSeconds(2));

        org.assertj.core.api.Assertions.assertThat(store.failureCount("account-hash")).isZero();
    }

    @Test
    void concurrentAdmissionNeverExceedsThePerAccountThreshold() throws Exception {
        int requests = 12;
        CountDownLatch start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(requests)) {
            var futures = java.util.stream.IntStream.range(0, requests)
                    .mapToObj(ignored -> executor.submit(() -> {
                        assertThatCode(() -> start.await(5, TimeUnit.SECONDS)).doesNotThrowAnyException();
                        try {
                            limiter.acquire("concurrent@example.com");
                            return true;
                        } catch (LoginRateLimitExceededException blocked) {
                            return false;
                        }
                    })).toList();
            start.countDown();

            long admitted = 0;
            for (var future : futures) {
                if (future.get(5, TimeUnit.SECONDS)) admitted++;
            }
            org.assertj.core.api.Assertions.assertThat(admitted).isEqualTo(3);
        }
    }

    @Test
    void releasedNonCredentialFailureDoesNotConsumeAnAttempt() {
        limiter.acquire("user@example.com");
        limiter.release("user@example.com");

        limiter.acquire("user@example.com");
        limiter.acquire("user@example.com");
        assertThatCode(() -> limiter.acquire("user@example.com")).doesNotThrowAnyException();
    }

    private static final class MutableTicker implements Ticker {
        private final AtomicLong nanos = new AtomicLong();

        @Override public long read() { return nanos.get(); }
        void advance(Duration duration) { nanos.addAndGet(duration.toNanos()); }
    }
}

package com.ibm.consulting.sim.identity.application;

import com.github.benmanes.caffeine.cache.Ticker;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Locale;

/**
 * Per-account protection for the expensive password-verification path. Storage
 * is distributed when the configured cache provider is Upstash and bounded
 * in-process when the application uses its default Caffeine cache.
 */
@Component
public class LoginAttemptLimiter {

    private final LoginAttemptProperties properties;
    private final LoginAttemptStore store;

    @Autowired
    public LoginAttemptLimiter(LoginAttemptProperties properties, LoginAttemptStore store) {
        validate(properties);
        this.properties = properties;
        this.store = store;
    }

    LoginAttemptLimiter(LoginAttemptProperties properties, Ticker ticker) {
        this(properties, new CaffeineLoginAttemptStore(properties, ticker));
    }

    private static void validate(LoginAttemptProperties properties) {
        if (properties.getMaxFailures() < 1 || properties.getWindow().isZero()
                || properties.getWindow().isNegative() || properties.getMaximumTrackedAccounts() < 1) {
            throw new IllegalArgumentException("Login attempt limits must be positive");
        }
    }

    public void acquire(String email) {
        if (!store.tryAcquire(key(email))) {
            throw new LoginRateLimitExceededException(properties.getWindow());
        }
    }

    public void release(String email) {
        store.release(key(email));
    }

    public void recordSuccess(String email) {
        store.reset(key(email));
    }

    private String key(String email) {
        String normalized = email.trim().toLowerCase(Locale.ROOT);
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(normalized.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is required by the Java runtime", impossible);
        }
    }
}

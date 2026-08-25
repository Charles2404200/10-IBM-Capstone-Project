package com.ibm.consulting.sim.identity.application;

import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.UUID;

/** Issues opaque, high-entropy credentials while persisting only a SHA-256 digest. */
@Component
public class CredentialTokenService {

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    public IssuedCredential issue() {
        byte[] secretBytes = new byte[32];
        SECURE_RANDOM.nextBytes(secretBytes);
        String secret = Base64.getUrlEncoder().withoutPadding().encodeToString(secretBytes);
        return new IssuedCredential(UUID.randomUUID().toString(), secret, hash(secret));
    }

    public boolean matches(String expectedHash, String suppliedSecret) {
        return MessageDigest.isEqual(expectedHash.getBytes(StandardCharsets.UTF_8),
                hash(suppliedSecret).getBytes(StandardCharsets.UTF_8));
    }

    public Instant expiresAt(long ttlMinutes) {
        return Instant.now().plus(ttlMinutes, ChronoUnit.MINUTES);
    }

    public ParsedCredential parse(String compactToken) {
        if (compactToken == null) {
            throw new IllegalArgumentException("Credential token is required.");
        }
        String[] parts = compactToken.split("\\.", -1);
        if (parts.length != 2 || parts[0].isBlank() || parts[1].isBlank()) {
            throw new IllegalArgumentException("Credential token format is invalid.");
        }
        return new ParsedCredential(parts[0], parts[1]);
    }

    private String hash(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(digest);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 must be available in this JVM", ex);
        }
    }

    public record IssuedCredential(String selector, String secret, String hash) {
        public String compactToken() { return selector + "." + secret; }
    }

    public record ParsedCredential(String selector, String secret) {}
}

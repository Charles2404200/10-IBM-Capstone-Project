package com.ibm.consulting.sim.identity.domain;

import com.ibm.consulting.sim.shared.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/** One-time, hashed credential used to prove control of a registration inbox. */
@Entity
@Table(name = "email_verification_token")
public class EmailVerificationToken extends BaseEntity {

    @Column(nullable = false, updatable = false)
    private UUID userId;

    @Column(nullable = false, unique = true, updatable = false, length = 36)
    private String selector;

    @Column(nullable = false, updatable = false)
    private String tokenHash;

    @Column(nullable = false, updatable = false)
    private Instant expiresAt;

    private Instant verifiedAt;
    private Instant revokedAt;

    protected EmailVerificationToken() {}

    public static EmailVerificationToken issue(UUID userId, String selector, String tokenHash, Instant expiresAt) {
        EmailVerificationToken token = new EmailVerificationToken();
        token.userId = userId;
        token.selector = selector;
        token.tokenHash = tokenHash;
        token.expiresAt = expiresAt;
        return token;
    }

    public boolean isUsableAt(Instant now) {
        return verifiedAt == null && revokedAt == null && expiresAt.isAfter(now);
    }

    public boolean isVerified() { return verifiedAt != null; }

    public void markVerified(Instant now) { this.verifiedAt = now; }
    public void revoke(Instant now) { this.revokedAt = now; }

    public UUID getUserId() { return userId; }
    public String getSelector() { return selector; }
    public String getTokenHash() { return tokenHash; }
    public Instant getExpiresAt() { return expiresAt; }
}

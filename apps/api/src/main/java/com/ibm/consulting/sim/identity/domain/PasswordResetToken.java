package com.ibm.consulting.sim.identity.domain;

import com.ibm.consulting.sim.shared.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/** One-time, hashed credential used to authorize a password replacement. */
@Entity
@Table(name = "password_reset_token")
public class PasswordResetToken extends BaseEntity {

    @Column(nullable = false, updatable = false)
    private UUID userId;

    @Column(nullable = false, unique = true, updatable = false, length = 36)
    private String selector;

    @Column(nullable = false, updatable = false)
    private String tokenHash;

    @Column(nullable = false, updatable = false)
    private Instant expiresAt;

    private Instant usedAt;
    private Instant revokedAt;

    protected PasswordResetToken() {}

    public static PasswordResetToken issue(UUID userId, String selector, String tokenHash, Instant expiresAt) {
        PasswordResetToken token = new PasswordResetToken();
        token.userId = userId;
        token.selector = selector;
        token.tokenHash = tokenHash;
        token.expiresAt = expiresAt;
        return token;
    }

    public boolean isUsableAt(Instant now) {
        return usedAt == null && revokedAt == null && expiresAt.isAfter(now);
    }

    public void markUsed(Instant now) { this.usedAt = now; }
    public void revoke(Instant now) { this.revokedAt = now; }

    public UUID getUserId() { return userId; }
    public String getSelector() { return selector; }
    public String getTokenHash() { return tokenHash; }
    public Instant getExpiresAt() { return expiresAt; }
}

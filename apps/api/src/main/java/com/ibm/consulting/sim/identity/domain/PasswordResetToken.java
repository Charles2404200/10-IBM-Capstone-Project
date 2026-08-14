package com.ibm.consulting.sim.identity.domain;

import com.ibm.consulting.sim.shared.domain.BaseEntity;
import jakarta.persistence.*;

import java.time.Instant;

@Entity
@Table(
        name = "password_reset_token",
        indexes = {
                @Index(
                        name = "idx_password_reset_token_selector",
                        columnList = "selector",
                        unique = true
                )
        }
)
public class PasswordResetToken extends BaseEntity {

    @Column(nullable = false, unique = true)
    private String selector;

    @Column(name = "token_hash", nullable = false)
    private String tokenHash;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "used_at")
    private Instant usedAt;

    @Column(name = "revoked_at")
    private Instant revokedAt;

    protected PasswordResetToken() {
    }

    private PasswordResetToken(
            String selector,
            String tokenHash,
            User user,
            Instant expiresAt
    ) {
        this.selector = selector;
        this.tokenHash = tokenHash;
        this.user = user;
        this.expiresAt = expiresAt;
    }

    public static PasswordResetToken create(
            String selector,
            String tokenHash,
            User user,
            Instant expiresAt
    ) {
        return new PasswordResetToken(
                selector,
                tokenHash,
                user,
                expiresAt
        );
    }

    public boolean isExpired() {
        return Instant.now().isAfter(expiresAt);
    }

    public boolean isUsed() {
        return usedAt != null;
    }

    public boolean isRevoked() {
        return revokedAt != null;
    }

    public boolean isUsable() {
        return !isUsed()
                && !isRevoked()
                && !isExpired();
    }

    public void markUsed() {
        this.usedAt = Instant.now();
    }

    public void revoke() {
        this.revokedAt = Instant.now();
    }

    public String getSelector() {
        return selector;
    }

    public String getTokenHash() {
        return tokenHash;
    }

    public User getUser() {
        return user;
    }
}
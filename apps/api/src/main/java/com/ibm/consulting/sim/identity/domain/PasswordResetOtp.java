package com.ibm.consulting.sim.identity.domain;

import com.ibm.consulting.sim.shared.domain.BaseEntity;
import jakarta.persistence.*;

import java.time.Instant;

@Entity
@Table(name = "password_reset_otp")
public class PasswordResetOtp extends BaseEntity {

    @Column(name = "otp_hash", nullable = false)
    private String otpHash;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "verified_at")
    private Instant verifiedAt;

    @Column(name = "revoked_at")
    private Instant revokedAt;

    protected PasswordResetOtp() {
    }

    private PasswordResetOtp(
            String otpHash,
            User user,
            Instant expiresAt
    ) {
        this.otpHash = otpHash;
        this.user = user;
        this.expiresAt = expiresAt;
    }

    public static PasswordResetOtp create(
            String otpHash,
            User user,
            Instant expiresAt
    ) {
        return new PasswordResetOtp(
                otpHash,
                user,
                expiresAt
        );
    }

    public boolean isExpired() {
        return Instant.now().isAfter(expiresAt);
    }

    public boolean isVerified() {
        return verifiedAt != null;
    }

    public boolean isRevoked() {
        return revokedAt != null;
    }

    public boolean isUsable() {
        return !isVerified()
                && !isRevoked()
                && !isExpired();
    }

    public void markVerified() {
        this.verifiedAt = Instant.now();
    }

    public void revoke() {
        this.revokedAt = Instant.now();
    }

    public String getOtpHash() {
        return otpHash;
    }

    public User getUser() {
        return user;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }
}
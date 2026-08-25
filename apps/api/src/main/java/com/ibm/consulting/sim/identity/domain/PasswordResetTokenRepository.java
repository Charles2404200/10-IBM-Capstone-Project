package com.ibm.consulting.sim.identity.domain;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface PasswordResetTokenRepository {
    PasswordResetToken save(PasswordResetToken token);
    Optional<PasswordResetToken> findBySelector(String selector);
    Optional<PasswordResetToken> findLatestByUserId(UUID userId);
    void revokeActiveForUser(UUID userId, Instant now);
}

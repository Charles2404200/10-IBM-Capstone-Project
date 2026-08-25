package com.ibm.consulting.sim.identity.domain;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface EmailVerificationTokenRepository {
    EmailVerificationToken save(EmailVerificationToken token);
    Optional<EmailVerificationToken> findBySelector(String selector);
    Optional<EmailVerificationToken> findLatestByUserId(UUID userId);
    void revokeActiveForUser(UUID userId, Instant now);
}

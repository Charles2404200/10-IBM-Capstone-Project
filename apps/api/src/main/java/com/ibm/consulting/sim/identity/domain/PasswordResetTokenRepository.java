package com.ibm.consulting.sim.identity.domain;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PasswordResetTokenRepository {

    PasswordResetToken save(
            PasswordResetToken token
    );

    Optional<PasswordResetToken> findBySelector(
            String selector
    );

    void revokeAllActiveTokensByUserId(
            UUID userId
    );

    Optional<PasswordResetToken>  findBySelectorForUpdate(String tokenHash);
}
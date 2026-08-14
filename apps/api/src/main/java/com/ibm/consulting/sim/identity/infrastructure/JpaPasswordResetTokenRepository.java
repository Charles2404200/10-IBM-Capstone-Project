package com.ibm.consulting.sim.identity.infrastructure;

import com.ibm.consulting.sim.identity.domain.PasswordResetToken;
import com.ibm.consulting.sim.identity.domain.PasswordResetTokenRepository;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
interface SpringDataPasswordResetTokenRepository
        extends JpaRepository<PasswordResetToken, UUID> {

    Optional<PasswordResetToken> findBySelector(String selector);

    List<PasswordResetToken>
    findByUser_IdAndUsedAtIsNullAndRevokedAtIsNullAndExpiresAtAfter(
            UUID userId,
            Instant now
    );
}

@Repository
class JpaPasswordResetTokenRepository
        implements PasswordResetTokenRepository {

    private final SpringDataPasswordResetTokenRepository repo;

    JpaPasswordResetTokenRepository(
            SpringDataPasswordResetTokenRepository repo
    ) {
        this.repo = repo;
    }

    @Override
    public PasswordResetToken save(
            PasswordResetToken token
    ) {
        return repo.save(token);
    }

    @Override
    public Optional<PasswordResetToken> findBySelector(
            String selector
    ) {
        return repo.findBySelector(selector);
    }

    @Override
    public void revokeAllActiveTokensByUserId(
            UUID userId
    ) {

        List<PasswordResetToken> activeTokens =
                repo.findByUser_IdAndUsedAtIsNullAndRevokedAtIsNullAndExpiresAtAfter(
                        userId,
                        Instant.now()
                );

        for (PasswordResetToken token : activeTokens) {
            token.revoke();
        }
    }
}
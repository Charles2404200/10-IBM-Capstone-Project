package com.ibm.consulting.sim.identity.infrastructure;

import com.ibm.consulting.sim.identity.domain.EmailVerificationToken;
import com.ibm.consulting.sim.identity.domain.EmailVerificationTokenRepository;
import com.ibm.consulting.sim.identity.domain.PasswordResetToken;
import com.ibm.consulting.sim.identity.domain.PasswordResetTokenRepository;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Repository
interface SpringDataEmailVerificationTokenRepository extends JpaRepository<EmailVerificationToken, UUID> {
    Optional<EmailVerificationToken> findBySelector(String selector);
    Optional<EmailVerificationToken> findTopByUserIdOrderByCreatedAtDesc(UUID userId);

    @Modifying
    @Query("update EmailVerificationToken token set token.revokedAt = :now "
            + "where token.userId = :userId and token.verifiedAt is null and token.revokedAt is null")
    void revokeActiveForUser(@Param("userId") UUID userId, @Param("now") Instant now);
}

@Repository
interface SpringDataPasswordResetTokenRepository extends JpaRepository<PasswordResetToken, UUID> {
    Optional<PasswordResetToken> findBySelector(String selector);
    Optional<PasswordResetToken> findTopByUserIdOrderByCreatedAtDesc(UUID userId);

    @Modifying
    @Query("update PasswordResetToken token set token.revokedAt = :now "
            + "where token.userId = :userId and token.usedAt is null and token.revokedAt is null")
    void revokeActiveForUser(@Param("userId") UUID userId, @Param("now") Instant now);
}

@Repository
class JpaEmailVerificationTokenRepository implements EmailVerificationTokenRepository {

    private final SpringDataEmailVerificationTokenRepository repository;

    JpaEmailVerificationTokenRepository(SpringDataEmailVerificationTokenRepository repository) {
        this.repository = repository;
    }

    @Override public EmailVerificationToken save(EmailVerificationToken token) { return repository.save(token); }
    @Override public Optional<EmailVerificationToken> findBySelector(String selector) { return repository.findBySelector(selector); }
    @Override public Optional<EmailVerificationToken> findLatestByUserId(UUID userId) { return repository.findTopByUserIdOrderByCreatedAtDesc(userId); }
    @Override public void revokeActiveForUser(UUID userId, Instant now) { repository.revokeActiveForUser(userId, now); }
}

@Repository
class JpaPasswordResetTokenRepository implements PasswordResetTokenRepository {

    private final SpringDataPasswordResetTokenRepository repository;

    JpaPasswordResetTokenRepository(SpringDataPasswordResetTokenRepository repository) {
        this.repository = repository;
    }

    @Override public PasswordResetToken save(PasswordResetToken token) { return repository.save(token); }
    @Override public Optional<PasswordResetToken> findBySelector(String selector) { return repository.findBySelector(selector); }
    @Override public Optional<PasswordResetToken> findLatestByUserId(UUID userId) { return repository.findTopByUserIdOrderByCreatedAtDesc(userId); }
    @Override public void revokeActiveForUser(UUID userId, Instant now) { repository.revokeActiveForUser(userId, now); }
}

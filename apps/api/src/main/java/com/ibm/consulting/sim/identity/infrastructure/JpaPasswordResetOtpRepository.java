package com.ibm.consulting.sim.identity.infrastructure;


import com.ibm.consulting.sim.identity.domain.PasswordResetOtp;
import com.ibm.consulting.sim.identity.domain.PasswordResetOtpRepository;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;


@Repository
interface SpringDataPasswordResetOtpRepository
        extends JpaRepository<PasswordResetOtp, UUID> {

    List<PasswordResetOtp>
    findByUser_IdAndVerifiedAtIsNullAndRevokedAtIsNullAndExpiresAtAfter(
            UUID userId,
            Instant now
    );
}

@Repository
class JpaPasswordResetOtpRepository implements PasswordResetOtpRepository {

    private final SpringDataPasswordResetOtpRepository repo;

    JpaPasswordResetOtpRepository(
            SpringDataPasswordResetOtpRepository repo
    ) {
        this.repo = repo;
    }

    @Override
    public PasswordResetOtp save(PasswordResetOtp otp) {
        return repo.save(otp);
    }

    @Override
    public List<PasswordResetOtp> findActiveOtpsByUserId(UUID userId) {
        return repo.findByUser_IdAndVerifiedAtIsNullAndRevokedAtIsNullAndExpiresAtAfter(
                userId,
                Instant.now()
        );
    }

    @Override
    public void revokeAllActiveOtpsByUserId(UUID userId) {

        List<PasswordResetOtp> activeOtps =
                repo.findByUser_IdAndVerifiedAtIsNullAndRevokedAtIsNullAndExpiresAtAfter(
                        userId,
                        Instant.now()
                );

        for (PasswordResetOtp otp : activeOtps) {
            otp.revoke();
            repo.save(otp);
        }
    }
}
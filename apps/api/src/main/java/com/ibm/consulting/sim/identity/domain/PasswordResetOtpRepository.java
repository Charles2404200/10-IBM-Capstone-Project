package com.ibm.consulting.sim.identity.domain;

import java.util.List;
import java.util.UUID;

public interface PasswordResetOtpRepository {

    PasswordResetOtp save(
            PasswordResetOtp otp
    );

    List<PasswordResetOtp> findActiveOtpsByUserId(
            UUID userId
    );

    void revokeAllActiveOtpsByUserId(
            UUID userId
    );
}
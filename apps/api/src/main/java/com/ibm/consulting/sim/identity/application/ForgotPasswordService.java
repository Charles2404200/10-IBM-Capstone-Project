package com.ibm.consulting.sim.identity.application;

import com.ibm.consulting.sim.identity.domain.*;
import com.ibm.consulting.sim.shared.domain.RateLimitExceededException;
import com.ibm.consulting.sim.shared.domain.RateLimiterService;
import jakarta.transaction.Transactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.*;


@Service
public class ForgotPasswordService {

    private final UserRepository userRepository;
    private final BaseEmailService emailService;
    private final PasswordResetOtpRepository passwordResetOtpRepository;
    private final PasswordResetTokenRepository passwordResetTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final RateLimiterService rateLimiterService;

    private static final Logger log =
            LoggerFactory.getLogger(ForgotPasswordService.class);

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    @Value("${app.password-reset.otp-expiry-minutes:10}")
    private int otpExpiryMinutes;

    @Value("${app.password-reset.reset-token-expiry-minutes:15}")
    private int resetTokenExpiryMinutes;

    public ForgotPasswordService(
            UserRepository userRepository,
            BaseEmailService emailService,
            PasswordResetOtpRepository passwordResetOtpRepository,
            PasswordResetTokenRepository passwordResetTokenRepository,
            PasswordEncoder passwordEncoder,
            RateLimiterService rateLimiterService
    ) {
        this.userRepository = userRepository;
        this.emailService = emailService;
        this.passwordResetOtpRepository = passwordResetOtpRepository;
        this.passwordResetTokenRepository = passwordResetTokenRepository;
        this.passwordEncoder = passwordEncoder;
        this.rateLimiterService = rateLimiterService;
    }

    @Transactional
    public void verifyEmail(String email) {

        log.info("Password reset requested");

        String normalizedEmail =
                email.trim().toLowerCase(Locale.ROOT);

        String key =
                "forgot-password:" +
                        hashIdentifier(normalizedEmail);

        boolean allowed =
                rateLimiterService.tryAcquire(
                        key,
                        5,
                        Duration.ofMinutes(15)
                );

        if (!allowed) {
            log.warn(
                    "Password reset request rate limit exceeded"
            );
            throw new RateLimitExceededException();
        }

        Optional<User> optionalUser =
                userRepository.findByEmail(normalizedEmail);

        if (optionalUser.isEmpty()) {
            /*
             * Important:
             * Don't log the email here.
             * Don't expose whether the account exists.
             */
            log.info("Password reset request processed");
            return;
        }

        User user = optionalUser.get();

        Integer otp = generateOtp();

        String otpHash =
                passwordEncoder.encode(otp.toString());

        passwordResetOtpRepository
                .revokeAllActiveOtpsByUserId(user.getId());

        passwordResetTokenRepository
                .revokeAllActiveTokensByUserId(user.getId());

        log.debug(
                "Previous password reset credentials revoked for userId={}",
                user.getId()
        );

        PasswordResetOtp resetOtp =
                PasswordResetOtp.create(
                        otpHash,
                        user,
                        Instant.now()
                                .plus(
                                        Duration.ofMinutes(
                                                otpExpiryMinutes
                                        )
                                )
                );

        passwordResetOtpRepository.save(resetOtp);

        log.info(
                "Password reset OTP created for userId={}",
                user.getId()
        );

        MailBody mailBody = new MailBody(
                email,
                "Password Reset OTP",
                createForgotPasswordEmailBody(
                        otp,
                        otpExpiryMinutes
                )
        );

        emailService.sendEmail(mailBody);

        log.info(
                "Password reset email sent for userId={}",
                user.getId()
        );
    }

    @Transactional
    public String verifyOtp(Integer otp, String email) {

        log.info("Password reset OTP verification requested");

        String normalizedEmail =
                email.trim().toLowerCase(Locale.ROOT);

        String rateLimitKey =
                "verify-otp:" + hashIdentifier(
                        normalizedEmail
                );

        boolean allowed =
                rateLimiterService.tryAcquire(
                        rateLimitKey,
                        5,
                        Duration.ofMinutes(10)
                );

        if (!allowed) {

            log.warn(
                    "Password reset OTP verification rate limit exceeded"
            );

            throw new RateLimitExceededException();
        }

        User user = userRepository.findByEmail(normalizedEmail)
                .orElseThrow(() -> {
                    /*
                     * Don't log email or OTP.
                     */
                    log.warn(
                            "Password reset OTP verification failed: account not resolved"
                    );

                    return new UserNotFoundException(email);
                });

        List<PasswordResetOtp> activeOtps =
                passwordResetOtpRepository
                        .findActiveOtpsByUserId(user.getId());

        PasswordResetOtp matchedOtp =
                activeOtps.stream()
                        .filter(PasswordResetOtp::isUsable)
                        .filter(storedOtp ->
                                passwordEncoder.matches(
                                        otp.toString(),
                                        storedOtp.getOtpHash()
                                )
                        )
                        .findFirst()
                        .orElseThrow(() -> {

                            log.warn(
                                    "Password reset OTP verification failed for userId={}",
                                    user.getId()
                            );

                            return new InvalidOtpException(
                                    "Otp is invalid"
                            );
                        });

        matchedOtp.markVerified();

        passwordResetOtpRepository.save(matchedOtp);

        log.info(
                "Password reset OTP successfully verified for userId={}",
                user.getId()
        );

        passwordResetTokenRepository
                .revokeAllActiveTokensByUserId(user.getId());

        GeneratedResetToken generatedToken =
                generateResetToken();

        String tokenHash =
                passwordEncoder.encode(
                        generatedToken.secret()
                );

        PasswordResetToken resetToken =
                PasswordResetToken.create(
                        generatedToken.selector(),
                        tokenHash,
                        user,
                        Instant.now()
                                .plus(
                                        Duration.ofMinutes(
                                                resetTokenExpiryMinutes
                                        )
                                )
                );

        passwordResetTokenRepository.save(resetToken);

        log.info(
                "Password reset token issued for userId={}",
                user.getId()
        );

        /*
         * NEVER log generatedToken.rawToken().
         */
        return generatedToken.rawToken();
    }

    @Transactional
    public void changePassword(
            String rawResetToken,
            String password,
            String repeatPassword
    ) {

        log.info("Password change using reset token requested");

        if (!password.equals(repeatPassword)) {

            log.warn(
                    "Password reset failed: password confirmation mismatch"
            );

            throw new PasswordMismatchException();
        }

        String[] tokenParts =
                rawResetToken.split("\\.", 2);

        if (tokenParts.length != 2) {

            log.warn(
                    "Password reset failed: malformed reset token"
            );

            throw new InvalidPasswordResetTokenException();
        }

        String selector = tokenParts[0];
        String secret = tokenParts[1];

        String rateLimitKey =
                "change-password:" + hashIdentifier(selector);

        boolean allowed =
                rateLimiterService.tryAcquire(
                        rateLimitKey,
                        10,
                        Duration.ofMinutes(15)
                );

        if (!allowed) {
            log.warn(
                    "Password reset token verification rate limit exceeded"
            );

            throw new RateLimitExceededException();
        }

        PasswordResetToken resetToken =
                passwordResetTokenRepository
                        .findBySelector(selector)
                        .orElseThrow(() -> {

                            log.warn(
                                    "Password reset failed: reset token not found"
                            );

                            return new InvalidPasswordResetTokenException();
                        });

        if (!resetToken.isUsable()) {

            log.warn(
                    "Password reset failed: reset token unusable for userId={}",
                    resetToken.getUser().getId()
            );

            throw new InvalidPasswordResetTokenException();
        }

        if (!passwordEncoder.matches(
                secret,
                resetToken.getTokenHash()
        )) {

            log.warn(
                    "Password reset failed: reset token verification failed for userId={}",
                    resetToken.getUser().getId()
            );

            throw new InvalidPasswordResetTokenException();
        }

        User user = resetToken.getUser();

        String newPasswordHash =
                passwordEncoder.encode(password);

        user.changePassword(newPasswordHash);

        resetToken.markUsed();

        userRepository.save(user);

        passwordResetTokenRepository.save(resetToken);

        passwordResetOtpRepository
                .revokeAllActiveOtpsByUserId(user.getId());

        log.info(
                "Password successfully reset for userId={}",
                user.getId()
        );
    }

    private String hashIdentifier(String value) {
        try {
            MessageDigest digest =
                    MessageDigest.getInstance("SHA-256");

            byte[] hash =
                    digest.digest(
                            value.getBytes(StandardCharsets.UTF_8)
                    );

            return HexFormat.of().formatHex(hash);

        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(
                    "SHA-256 is not available",
                    e
            );
        }
    }

    private Integer generateOtp() {
        return SECURE_RANDOM.nextInt(900_000) + 100_000;
    }

    private GeneratedResetToken generateResetToken() {

        String selector =
                UUID.randomUUID().toString();

        byte[] randomBytes = new byte[32];

        SECURE_RANDOM.nextBytes(randomBytes);

        String secret =
                Base64.getUrlEncoder()
                        .withoutPadding()
                        .encodeToString(randomBytes);

        String rawToken =
                selector + "." + secret;

        return new GeneratedResetToken(
                selector,
                secret,
                rawToken
        );
    }

    private String createForgotPasswordEmailBody(
            Integer otp,
            int expiryMinutes
    ) {
        return """
                Hi,

                We received a request to reset your password.

                Your one-time password (OTP) is:

                %d

                This OTP is valid for %d minutes.

                For security, do not share this OTP with anyone.

                If you did not request a password reset, you can safely ignore this email.

                Regards,
                Consulting Simulation Team
                """.formatted(otp, expiryMinutes);
    }

    private record GeneratedResetToken(
            String selector,
            String secret,
            String rawToken
    ) {}
}

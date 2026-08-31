package com.ibm.consulting.sim.identity.application;

import com.ibm.consulting.sim.identity.domain.EmailVerificationToken;
import com.ibm.consulting.sim.identity.domain.EmailVerificationTokenRepository;
import com.ibm.consulting.sim.identity.domain.InvalidCredentialTokenException;
import com.ibm.consulting.sim.identity.domain.User;
import com.ibm.consulting.sim.identity.domain.UserRepository;
import com.ibm.consulting.sim.shared.email.application.TransactionalEmailPublisher;
import com.ibm.consulting.sim.shared.email.template.TransactionalEmailTemplates;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Locale;

/** Owns resend, verification and invalidation rules for account-confirmation credentials. */
@Service
public class EmailVerificationService {

    private final UserRepository users;
    private final EmailVerificationTokenRepository tokens;
    private final CredentialTokenService credentialTokenService;
    private final TransactionalEmailPublisher emailPublisher;
    private final TransactionalEmailTemplates emailTemplates;
    private final IdentityEmailProperties properties;

    public EmailVerificationService(UserRepository users, EmailVerificationTokenRepository tokens,
                                    CredentialTokenService credentialTokenService,
                                    TransactionalEmailPublisher emailPublisher,
                                    TransactionalEmailTemplates emailTemplates,
                                    IdentityEmailProperties properties) {
        this.users = users;
        this.tokens = tokens;
        this.credentialTokenService = credentialTokenService;
        this.emailPublisher = emailPublisher;
        this.emailTemplates = emailTemplates;
        this.properties = properties;
    }

    @Transactional
    public void resend(String email) {
        users.findByEmail(normalise(email)).filter(User::isActive).filter(user -> !user.isEmailVerified())
                .ifPresent(this::issueAndDeliverUnlessCoolingDown);
    }

    @Transactional
    public void verify(String suppliedToken) {
        CredentialTokenService.ParsedCredential credential = credentialTokenService.parse(suppliedToken);
        EmailVerificationToken token = tokens.findBySelector(credential.selector())
                .orElseThrow(() -> new InvalidCredentialTokenException("email verification"));
        if (!credentialTokenService.matches(token.getTokenHash(), credential.secret())) {
            throw new InvalidCredentialTokenException("email verification");
        }
        User user = users.findById(token.getUserId())
                .orElseThrow(() -> new InvalidCredentialTokenException("email verification"));
        if (!user.isActive()) {
            throw new InvalidCredentialTokenException("email verification");
        }

        // A user may refresh or reopen the original confirmation link after success.
        // Accept only the same verified credential; expired or revoked unverified tokens remain invalid.
        if (token.isVerified() && user.isEmailVerified()) {
            return;
        }

        Instant now = Instant.now();
        if (!token.isUsableAt(now)) {
            throw new InvalidCredentialTokenException("email verification");
        }
        user.verifyEmail(now);
        token.markVerified(now);
        tokens.revokeActiveForUser(user.getId(), now);
    }

    private void issueAndDeliverUnlessCoolingDown(User user) {
        Instant now = Instant.now();
        if (tokens.findLatestByUserId(user.getId())
                .filter(token -> token.getCreatedAt().plusSeconds(properties.getResendCooldownSeconds()).isAfter(now))
                .isPresent()) {
            return;
        }
        tokens.revokeActiveForUser(user.getId(), now);
        CredentialTokenService.IssuedCredential credential = credentialTokenService.issue();
        tokens.save(EmailVerificationToken.issue(user.getId(), credential.selector(), credential.hash(),
                credentialTokenService.expiresAt(properties.getVerificationTtlMinutes())));
        emailPublisher.publish(emailTemplates.verification(user.getEmail(), user.getDisplayName(),
                properties.verificationUrl(credential.compactToken())));
    }

    private String normalise(String email) { return email.trim().toLowerCase(Locale.ROOT); }
}

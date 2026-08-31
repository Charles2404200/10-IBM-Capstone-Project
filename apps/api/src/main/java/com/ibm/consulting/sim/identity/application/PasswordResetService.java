package com.ibm.consulting.sim.identity.application;

import com.ibm.consulting.sim.identity.domain.InvalidCredentialTokenException;
import com.ibm.consulting.sim.identity.domain.PasswordResetToken;
import com.ibm.consulting.sim.identity.domain.PasswordResetTokenRepository;
import com.ibm.consulting.sim.identity.domain.User;
import com.ibm.consulting.sim.identity.domain.UserRepository;
import com.ibm.consulting.sim.shared.email.application.TransactionalEmailPublisher;
import com.ibm.consulting.sim.shared.email.template.TransactionalEmailTemplates;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Locale;

/** Password recovery always stores an opaque one-time credential as a digest. */
@Service
public class PasswordResetService {

    private final UserRepository users;
    private final PasswordResetTokenRepository tokens;
    private final CredentialTokenService credentialTokenService;
    private final PasswordEncoder passwordEncoder;
    private final TransactionalEmailPublisher emailPublisher;
    private final TransactionalEmailTemplates emailTemplates;
    private final IdentityEmailProperties properties;

    public PasswordResetService(UserRepository users, PasswordResetTokenRepository tokens,
                                CredentialTokenService credentialTokenService, PasswordEncoder passwordEncoder,
                                TransactionalEmailPublisher emailPublisher, TransactionalEmailTemplates emailTemplates,
                                IdentityEmailProperties properties) {
        this.users = users;
        this.tokens = tokens;
        this.credentialTokenService = credentialTokenService;
        this.passwordEncoder = passwordEncoder;
        this.emailPublisher = emailPublisher;
        this.emailTemplates = emailTemplates;
        this.properties = properties;
    }

    /** Deliberately silent for unknown/inactive/unverified accounts to avoid account enumeration. */
    @Transactional
    public void request(String email) {
        users.findByEmail(normalise(email)).filter(User::isActive).filter(User::isEmailVerified)
                .ifPresent(this::issueAndDeliverUnlessCoolingDown);
    }

    @Transactional
    public void reset(String suppliedToken, String newPassword) {
        CredentialTokenService.ParsedCredential credential = credentialTokenService.parse(suppliedToken);
        PasswordResetToken token = tokens.findBySelector(credential.selector())
                .orElseThrow(() -> new InvalidCredentialTokenException("password reset"));
        Instant now = Instant.now();
        if (!token.isUsableAt(now) || !credentialTokenService.matches(token.getTokenHash(), credential.secret())) {
            throw new InvalidCredentialTokenException("password reset");
        }
        User user = users.findById(token.getUserId())
                .orElseThrow(() -> new InvalidCredentialTokenException("password reset"));
        if (!user.isActive() || !user.isEmailVerified()) {
            throw new InvalidCredentialTokenException("password reset");
        }
        user.changePasswordHash(passwordEncoder.encode(newPassword));
        token.markUsed(now);
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
        tokens.save(PasswordResetToken.issue(user.getId(), credential.selector(), credential.hash(),
                credentialTokenService.expiresAt(properties.getPasswordResetTtlMinutes())));
        emailPublisher.publish(emailTemplates.passwordReset(user.getEmail(), user.getDisplayName(),
                properties.passwordResetUrl(credential.compactToken())));
    }

    private String normalise(String email) { return email.trim().toLowerCase(Locale.ROOT); }
}

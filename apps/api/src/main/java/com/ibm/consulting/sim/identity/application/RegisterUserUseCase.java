package com.ibm.consulting.sim.identity.application;

import com.ibm.consulting.sim.identity.domain.*;
import com.ibm.consulting.sim.shared.email.application.EmailDeliveryGateway;
import com.ibm.consulting.sim.shared.email.template.TransactionalEmailTemplates;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;

@Service
public class RegisterUserUseCase {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final EmailVerificationTokenRepository verificationTokens;
    private final CredentialTokenService credentialTokenService;
    private final EmailDeliveryGateway emailDeliveryGateway;
    private final TransactionalEmailTemplates emailTemplates;
    private final IdentityEmailProperties emailProperties;

    public RegisterUserUseCase(UserRepository userRepository, PasswordEncoder passwordEncoder,
                               EmailVerificationTokenRepository verificationTokens,
                               CredentialTokenService credentialTokenService,
                               EmailDeliveryGateway emailDeliveryGateway,
                               TransactionalEmailTemplates emailTemplates,
                               IdentityEmailProperties emailProperties) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.verificationTokens = verificationTokens;
        this.credentialTokenService = credentialTokenService;
        this.emailDeliveryGateway = emailDeliveryGateway;
        this.emailTemplates = emailTemplates;
        this.emailProperties = emailProperties;
    }

    @Transactional
    public RegistrationResponse execute(String email, String password, String displayName) {
        String normalisedEmail = email.trim().toLowerCase(Locale.ROOT);
        if (userRepository.existsByEmail(normalisedEmail)) {
            throw new UserAlreadyExistsException(normalisedEmail);
        }
        User user = User.createUnverified(normalisedEmail, passwordEncoder.encode(password), displayName.trim());
        userRepository.save(user);
        CredentialTokenService.IssuedCredential credential = credentialTokenService.issue();
        verificationTokens.save(EmailVerificationToken.issue(user.getId(), credential.selector(),
                credential.hash(), credentialTokenService.expiresAt(emailProperties.getVerificationTtlMinutes())));
        emailDeliveryGateway.send(emailTemplates.verification(user.getEmail(), user.getDisplayName(),
                emailProperties.verificationUrl(credential.compactToken())));
        return new RegistrationResponse(user.getEmail(), true);
    }
}

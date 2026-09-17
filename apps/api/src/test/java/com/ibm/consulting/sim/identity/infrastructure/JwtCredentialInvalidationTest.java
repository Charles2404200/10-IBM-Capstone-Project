package com.ibm.consulting.sim.identity.infrastructure;

import com.ibm.consulting.sim.identity.application.AuthenticateUseCase;
import com.ibm.consulting.sim.identity.application.CredentialTokenService;
import com.ibm.consulting.sim.identity.application.IdentityEmailProperties;
import com.ibm.consulting.sim.identity.application.PasswordResetService;
import com.ibm.consulting.sim.identity.domain.PasswordResetToken;
import com.ibm.consulting.sim.identity.domain.PasswordResetTokenRepository;
import com.ibm.consulting.sim.identity.domain.User;
import com.ibm.consulting.sim.identity.domain.UserRepository;
import com.ibm.consulting.sim.identity.domain.UserRole;
import com.ibm.consulting.sim.shared.email.application.TransactionalEmailPublisher;
import com.ibm.consulting.sim.shared.email.template.TransactionalEmailTemplates;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class JwtCredentialInvalidationTest {

    private static final String JWT_SECRET = "test-secret-that-is-long-enough-for-hmac-sha-256-signatures";

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void passwordResetInvalidatesEarlierJwtButAllowsLoginWithTheNewPassword() throws Exception {
        User user = User.create("learner@example.com", "old-password-hash", "Learner", UserRole.LEARNER);
        UserRepository users = mock(UserRepository.class);
        when(users.findById(user.getId())).thenReturn(Optional.of(user));
        when(users.findByIdForUpdate(user.getId())).thenReturn(Optional.of(user));
        when(users.findByEmail(user.getEmail())).thenReturn(Optional.of(user));

        PasswordEncoder passwordEncoder = mock(PasswordEncoder.class);
        when(passwordEncoder.encode("NewStrongPass123!")).thenReturn("new-password-hash");
        when(passwordEncoder.matches("NewStrongPass123!", "new-password-hash")).thenReturn(true);

        JwtTokenProvider tokenProvider = new JwtTokenProvider(JWT_SECRET, 60_000);
        String originalToken = tokenProvider.generateToken(user);
        JwtAuthenticationFilter filter = new JwtAuthenticationFilter(tokenProvider, users);

        assertThat(authenticate(filter, originalToken)).isNotNull();

        user.changeRole(UserRole.REVIEWER);
        assertThat(authenticate(filter, originalToken)).isNotNull();

        CredentialTokenService credentialTokens = new CredentialTokenService();
        CredentialTokenService.IssuedCredential resetCredential = credentialTokens.issue();
        PasswordResetToken resetToken = PasswordResetToken.issue(
                user.getId(), resetCredential.selector(), resetCredential.hash(), Instant.now().plusSeconds(60));
        PasswordResetTokenRepository resetTokens = mock(PasswordResetTokenRepository.class);
        when(resetTokens.findUserIdBySelector(resetCredential.selector())).thenReturn(Optional.of(user.getId()));
        when(resetTokens.findBySelectorForUpdate(resetCredential.selector())).thenReturn(Optional.of(resetToken));
        PasswordResetService resetService = new PasswordResetService(
                users,
                resetTokens,
                credentialTokens,
                passwordEncoder,
                mock(TransactionalEmailPublisher.class),
                mock(TransactionalEmailTemplates.class),
                new IdentityEmailProperties());

        resetService.reset(resetCredential.compactToken(), "NewStrongPass123!");

        assertThat(authenticate(filter, originalToken)).isNull();

        AuthenticateUseCase authenticateUseCase = new AuthenticateUseCase(users, passwordEncoder, tokenProvider);
        String replacementToken = authenticateUseCase
                .execute(user.getEmail(), "NewStrongPass123!")
                .accessToken();
        assertThat(authenticate(filter, replacementToken)).isNotNull();
    }

    private Authentication authenticate(JwtAuthenticationFilter filter, String token) throws Exception {
        SecurityContextHolder.clearContext();
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer " + token);
        AtomicReference<Authentication> authentication = new AtomicReference<>();

        filter.doFilter(request, new MockHttpServletResponse(),
                (ignoredRequest, ignoredResponse) ->
                        authentication.set(SecurityContextHolder.getContext().getAuthentication()));

        SecurityContextHolder.clearContext();
        return authentication.get();
    }
}

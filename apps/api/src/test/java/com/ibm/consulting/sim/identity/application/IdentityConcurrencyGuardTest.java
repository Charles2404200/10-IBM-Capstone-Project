package com.ibm.consulting.sim.identity.application;

import com.ibm.consulting.sim.identity.domain.EmailVerificationToken;
import com.ibm.consulting.sim.identity.domain.EmailVerificationTokenRepository;
import com.ibm.consulting.sim.identity.domain.InvalidCredentialTokenException;
import com.ibm.consulting.sim.identity.domain.User;
import com.ibm.consulting.sim.identity.domain.UserAlreadyExistsException;
import com.ibm.consulting.sim.identity.domain.UserRepository;
import com.ibm.consulting.sim.shared.email.application.TransactionalEmailPublisher;
import com.ibm.consulting.sim.shared.email.template.TransactionalEmailTemplates;
import org.hibernate.exception.ConstraintViolationException;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class IdentityConcurrencyGuardTest {

    @Test
    void translatesOnlyTheUniqueEmailConstraintBeforeCreatingSideEffects() {
        UserRepository users = mock(UserRepository.class);
        EmailVerificationTokenRepository tokens = mock(EmailVerificationTokenRepository.class);
        TransactionalEmailPublisher emails = mock(TransactionalEmailPublisher.class);
        ConstraintViolationException uniqueEmail = mock(ConstraintViolationException.class);
        when(uniqueEmail.getConstraintName()).thenReturn("uq_users_email");
        when(users.saveAndFlush(any())).thenThrow(new DataIntegrityViolationException("duplicate", uniqueEmail));
        RegisterUserUseCase service = registrationService(users, tokens, emails);

        assertThatThrownBy(() -> service.execute(" Duplicate@Example.com ", "StrongPassword123!", "Learner"))
                .isInstanceOf(UserAlreadyExistsException.class);
        verify(tokens, never()).save(any());
        verify(emails, never()).publish(any());
    }

    @Test
    void doesNotMisclassifyAnUnrelatedIntegrityViolationAsDuplicateEmail() {
        UserRepository users = mock(UserRepository.class);
        ConstraintViolationException otherConstraint = mock(ConstraintViolationException.class);
        when(otherConstraint.getConstraintName()).thenReturn("some_other_constraint");
        DataIntegrityViolationException failure = new DataIntegrityViolationException("invalid", otherConstraint);
        when(users.saveAndFlush(any())).thenThrow(failure);
        RegisterUserUseCase service = registrationService(
                users, mock(EmailVerificationTokenRepository.class), mock(TransactionalEmailPublisher.class));

        assertThatThrownBy(() -> service.execute("learner@example.com", "StrongPassword123!", "Learner"))
                .isSameAs(failure);
    }

    @Test
    void verificationRechecksTheLockedCredentialAndRemainsRepeatSafe() {
        CredentialTokenService credentials = new CredentialTokenService();
        CredentialTokenService.IssuedCredential issued = credentials.issue();
        User user = User.createUnverified("verify@example.com", "hash", "Learner");
        EmailVerificationToken token = EmailVerificationToken.issue(
                user.getId(), issued.selector(), issued.hash(), Instant.now().plusSeconds(60));
        UserRepository users = mock(UserRepository.class);
        EmailVerificationTokenRepository tokens = mock(EmailVerificationTokenRepository.class);
        when(tokens.findUserIdBySelector(issued.selector())).thenReturn(Optional.of(user.getId()));
        when(users.findByIdForUpdate(user.getId())).thenReturn(Optional.of(user));
        when(tokens.findBySelectorForUpdate(issued.selector())).thenReturn(Optional.of(token));
        EmailVerificationService service = verificationService(users, tokens, credentials);

        service.verify(issued.compactToken());
        Instant verifiedAt = user.getEmailVerifiedAt();
        service.verify(issued.compactToken());

        assertThat(user.isEmailVerified()).isTrue();
        assertThat(token.isVerified()).isTrue();
        assertThat(user.getEmailVerifiedAt()).isEqualTo(verifiedAt);
        verify(users, times(2)).findByIdForUpdate(user.getId());
        verify(tokens, times(2)).findBySelectorForUpdate(issued.selector());
        verify(tokens, times(1)).revokeActiveForUser(user.getId(), verifiedAt);
    }

    @Test
    void revokedAndExpiredVerificationCredentialsRemainInvalidAfterLocking() {
        CredentialTokenService credentials = new CredentialTokenService();
        CredentialTokenService.IssuedCredential revokedCredential = credentials.issue();
        CredentialTokenService.IssuedCredential expiredCredential = credentials.issue();
        User user = User.createUnverified("invalid-token@example.com", "hash", "Learner");
        EmailVerificationToken revoked = EmailVerificationToken.issue(
                user.getId(), revokedCredential.selector(), revokedCredential.hash(), Instant.now().plusSeconds(60));
        revoked.revoke(Instant.now());
        EmailVerificationToken expired = EmailVerificationToken.issue(
                user.getId(), expiredCredential.selector(), expiredCredential.hash(), Instant.now().minusSeconds(1));
        UserRepository users = mock(UserRepository.class);
        EmailVerificationTokenRepository tokens = mock(EmailVerificationTokenRepository.class);
        when(users.findByIdForUpdate(user.getId())).thenReturn(Optional.of(user));
        when(tokens.findUserIdBySelector(revokedCredential.selector())).thenReturn(Optional.of(user.getId()));
        when(tokens.findBySelectorForUpdate(revokedCredential.selector())).thenReturn(Optional.of(revoked));
        when(tokens.findUserIdBySelector(expiredCredential.selector())).thenReturn(Optional.of(user.getId()));
        when(tokens.findBySelectorForUpdate(expiredCredential.selector())).thenReturn(Optional.of(expired));
        EmailVerificationService service = verificationService(users, tokens, credentials);

        assertThatThrownBy(() -> service.verify(revokedCredential.compactToken()))
                .isInstanceOf(InvalidCredentialTokenException.class);
        assertThatThrownBy(() -> service.verify(expiredCredential.compactToken()))
                .isInstanceOf(InvalidCredentialTokenException.class);
        assertThat(user.isEmailVerified()).isFalse();
    }

    @Test
    void onboardingUsesTheUserLockAndDoesNotRewriteCompletionTime() {
        User user = User.createUnverified("onboarding@example.com", "hash", "Learner");
        UserRepository users = mock(UserRepository.class);
        when(users.findByIdForUpdate(user.getId())).thenReturn(Optional.of(user));
        UserOnboardingService service = new UserOnboardingService(users);

        service.complete(user);
        Instant completedAt = user.getOnboardingCompletedAt();
        service.complete(user);

        assertThat(user.getOnboardingCompletedAt()).isEqualTo(completedAt);
        verify(users, times(2)).findByIdForUpdate(user.getId());
        verify(users, times(1)).save(user);
    }

    private RegisterUserUseCase registrationService(UserRepository users,
                                                     EmailVerificationTokenRepository tokens,
                                                     TransactionalEmailPublisher emails) {
        PasswordEncoder encoder = mock(PasswordEncoder.class);
        when(encoder.encode(any())).thenReturn("encoded");
        return new RegisterUserUseCase(users, encoder, tokens, new CredentialTokenService(), emails,
                new TransactionalEmailTemplates(), new IdentityEmailProperties());
    }

    private EmailVerificationService verificationService(UserRepository users,
                                                           EmailVerificationTokenRepository tokens,
                                                           CredentialTokenService credentials) {
        return new EmailVerificationService(users, tokens, credentials, mock(TransactionalEmailPublisher.class),
                new TransactionalEmailTemplates(), new IdentityEmailProperties());
    }
}

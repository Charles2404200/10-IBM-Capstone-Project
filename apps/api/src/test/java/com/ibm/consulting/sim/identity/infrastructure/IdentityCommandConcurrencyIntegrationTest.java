package com.ibm.consulting.sim.identity.infrastructure;

import com.ibm.consulting.sim.identity.application.CredentialTokenService;
import com.ibm.consulting.sim.identity.application.EmailVerificationService;
import com.ibm.consulting.sim.identity.application.IdentityEmailProperties;
import com.ibm.consulting.sim.identity.application.RegisterUserUseCase;
import com.ibm.consulting.sim.identity.application.UserOnboardingService;
import com.ibm.consulting.sim.identity.domain.EmailVerificationToken;
import com.ibm.consulting.sim.identity.domain.EmailVerificationTokenRepository;
import com.ibm.consulting.sim.identity.domain.User;
import com.ibm.consulting.sim.identity.domain.UserAlreadyExistsException;
import com.ibm.consulting.sim.identity.domain.UserRepository;
import com.ibm.consulting.sim.shared.email.application.TransactionalEmailPublisher;
import com.ibm.consulting.sim.shared.email.template.TransactionalEmailTemplates;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DataJpaTest
@Import({JpaUserRepository.class, JpaEmailVerificationTokenRepository.class})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers(disabledWithoutDocker = true)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class IdentityCommandConcurrencyIntegrationTest {

    @Container
    static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>(
            DockerImageName.parse("pgvector/pgvector:pg16").asCompatibleSubstituteFor("postgres"));

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @Autowired EntityManager entityManager;
    @Autowired UserRepository userRepository;
    @Autowired EmailVerificationTokenRepository verificationTokens;
    @Autowired PlatformTransactionManager transactionManager;

    @Test
    void concurrentCaseInsensitiveRegistrationCreatesOneAccountAndCredential() throws Exception {
        CountDownLatch existenceChecks = new CountDownLatch(2);
        TransactionalEmailPublisher emails = mock(TransactionalEmailPublisher.class);
        PasswordEncoder passwordEncoder = mock(PasswordEncoder.class);
        when(passwordEncoder.encode(any())).thenReturn("encoded-password");
        RegisterUserUseCase service = new RegisterUserUseCase(
                gateEmailExistenceChecks(userRepository, existenceChecks), passwordEncoder,
                verificationTokens, new CredentialTokenService(), emails,
                new TransactionalEmailTemplates(), new IdentityEmailProperties());

        List<Outcome> outcomes = runConcurrently(
                () -> service.execute("Concurrent@Example.com", "StrongPassword123!", "First"),
                () -> service.execute("concurrent@example.com", "StrongPassword123!", "Second"));

        assertThat(outcomes).filteredOn(Outcome::succeeded).hasSize(1);
        assertThat(outcomes).filteredOn(outcome -> !outcome.succeeded())
                .extracting(Outcome::failure)
                .allMatch(UserAlreadyExistsException.class::isInstance);
        assertThat(count("select count(user) from User user where user.email = 'concurrent@example.com'"))
                .isEqualTo(1L);
        UUID userId = inTransaction(() -> userRepository.findByEmail("concurrent@example.com").orElseThrow().getId());
        assertThat(countVerificationTokens(userId)).isEqualTo(1L);
        verify(emails, times(1)).publish(any());
    }

    @Test
    void concurrentOnboardingCompletionIsIdempotentAndKeepsTheFirstTimestamp() throws Exception {
        User authenticated = inTransaction(() -> {
            User user = User.createUnverified(UUID.randomUUID() + "@example.com", "hash", "New learner");
            entityManager.persist(user);
            entityManager.flush();
            return user;
        });
        UserOnboardingService service = new UserOnboardingService(userRepository);

        List<Outcome> outcomes = runConcurrently(
                () -> service.complete(authenticated),
                () -> service.complete(authenticated));

        assertThat(outcomes).allMatch(Outcome::succeeded);
        Instant completedAt = inTransaction(() -> userRepository.findById(authenticated.getId()).orElseThrow()
                .getOnboardingCompletedAt());
        assertThat(completedAt).isNotNull();

        inTransaction(() -> service.complete(authenticated));
        Instant afterRetry = inTransaction(() -> userRepository.findById(authenticated.getId()).orElseThrow()
                .getOnboardingCompletedAt());
        assertThat(afterRetry).isEqualTo(completedAt);
    }

    @Test
    void concurrentVerificationIsRepeatSafeAndRevokesSiblingCredentials() throws Exception {
        CredentialTokenService credentialService = new CredentialTokenService();
        CredentialTokenService.IssuedCredential credential = credentialService.issue();
        CredentialTokenService.IssuedCredential siblingCredential = credentialService.issue();
        UUID userId = inTransaction(() -> {
            User user = User.createUnverified(UUID.randomUUID() + "@example.com", "hash", "Unverified learner");
            entityManager.persist(user);
            verificationTokens.save(EmailVerificationToken.issue(user.getId(), credential.selector(), credential.hash(),
                    Instant.now().plusSeconds(600)));
            verificationTokens.save(EmailVerificationToken.issue(user.getId(), siblingCredential.selector(), siblingCredential.hash(),
                    Instant.now().plusSeconds(600)));
            entityManager.flush();
            return user.getId();
        });
        EmailVerificationService service = new EmailVerificationService(
                userRepository, verificationTokens, credentialService, mock(TransactionalEmailPublisher.class),
                new TransactionalEmailTemplates(), new IdentityEmailProperties());

        List<Outcome> outcomes = runConcurrently(
                () -> service.verify(credential.compactToken()),
                () -> service.verify(credential.compactToken()));

        assertThat(outcomes).allMatch(Outcome::succeeded);
        assertThat(inTransaction(() -> userRepository.findById(userId)).orElseThrow().isEmailVerified()).isTrue();
        EmailVerificationToken verified = inTransaction(
                () -> verificationTokens.findBySelector(credential.selector()).orElseThrow());
        EmailVerificationToken sibling = inTransaction(
                () -> verificationTokens.findBySelector(siblingCredential.selector()).orElseThrow());
        assertThat(verified.isVerified()).isTrue();
        assertThat(sibling.isUsableAt(Instant.now())).isFalse();

        inTransaction(() -> service.verify(credential.compactToken()));
    }

    private UserRepository gateEmailExistenceChecks(UserRepository delegate, CountDownLatch checksReached) {
        return new UserRepository() {
            @Override public User save(User user) { return delegate.save(user); }
            @Override public User saveAndFlush(User user) { return delegate.saveAndFlush(user); }
            @Override public Optional<User> findById(UUID id) { return delegate.findById(id); }
            @Override public Optional<User> findByIdForUpdate(UUID id) { return delegate.findByIdForUpdate(id); }
            @Override public Optional<User> findByEmail(String email) { return delegate.findByEmail(email); }
            @Override public Optional<User> findByEmailForUpdate(String email) { return delegate.findByEmailForUpdate(email); }
            @Override public boolean existsByEmail(String email) {
                boolean exists = delegate.existsByEmail(email);
                checksReached.countDown();
                awaitPeer(checksReached);
                return exists;
            }
            @Override public List<User> findAll() { return delegate.findAll(); }
        };
    }

    private List<Outcome> runConcurrently(Runnable firstCommand, Runnable secondCommand) throws Exception {
        CountDownLatch start = new CountDownLatch(1);
        try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
            Future<Outcome> first = executor.submit(() -> execute(start, firstCommand));
            Future<Outcome> second = executor.submit(() -> execute(start, secondCommand));
            start.countDown();
            return List.of(first.get(15, TimeUnit.SECONDS), second.get(15, TimeUnit.SECONDS));
        }
    }

    private Outcome execute(CountDownLatch start, Runnable command) {
        try {
            assertThat(start.await(5, TimeUnit.SECONDS)).isTrue();
            inTransaction(command);
            return new Outcome(true, null);
        } catch (RuntimeException exception) {
            return new Outcome(false, exception);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return new Outcome(false, exception);
        }
    }

    private void awaitPeer(CountDownLatch latch) {
        try {
            assertThat(latch.await(5, TimeUnit.SECONDS)).isTrue();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(exception);
        }
    }

    private long count(String query) {
        return inTransaction(() -> entityManager.createQuery(query, Long.class).getSingleResult());
    }

    private long countVerificationTokens(UUID userId) {
        return inTransaction(() -> entityManager.createQuery(
                        "select count(token) from EmailVerificationToken token where token.userId = :userId", Long.class)
                .setParameter("userId", userId)
                .getSingleResult());
    }

    private void inTransaction(Runnable work) {
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> work.run());
    }

    private <T> T inTransaction(java.util.concurrent.Callable<T> work) {
        return new TransactionTemplate(transactionManager).execute(status -> {
            try {
                return work.call();
            } catch (RuntimeException exception) {
                throw exception;
            } catch (Exception exception) {
                throw new IllegalStateException(exception);
            }
        });
    }

    private record Outcome(boolean succeeded, Throwable failure) {}
}

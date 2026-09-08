package com.ibm.consulting.sim.identity.infrastructure;

import com.ibm.consulting.sim.identity.application.CredentialTokenService;
import com.ibm.consulting.sim.identity.application.EmailVerificationService;
import com.ibm.consulting.sim.identity.application.IdentityEmailProperties;
import com.ibm.consulting.sim.identity.application.PasswordResetService;
import com.ibm.consulting.sim.identity.domain.User;
import com.ibm.consulting.sim.identity.domain.UserRepository;
import com.ibm.consulting.sim.identity.domain.UserRole;
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

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@DataJpaTest
@Import({JpaUserRepository.class, JpaPasswordResetTokenRepository.class, JpaEmailVerificationTokenRepository.class})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers(disabledWithoutDocker = true)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class CredentialIssuanceConcurrencyIntegrationTest {

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
    @Autowired com.ibm.consulting.sim.identity.domain.PasswordResetTokenRepository passwordResetTokens;
    @Autowired com.ibm.consulting.sim.identity.domain.EmailVerificationTokenRepository verificationTokens;
    @Autowired PlatformTransactionManager transactionManager;

    @Test
    void concurrentPasswordResetRequestsIssueOneCredentialAndOneEmail() throws Exception {
        User user = inTransaction(() -> persistUser(true));
        CountDownLatch readsReached = new CountDownLatch(2);
        TransactionalEmailPublisher publisher = mock(TransactionalEmailPublisher.class);
        PasswordResetService service = new PasswordResetService(
                gateEmailReads(userRepository, readsReached), passwordResetTokens, new CredentialTokenService(),
                mock(PasswordEncoder.class), publisher, new TransactionalEmailTemplates(), properties());

        runConcurrently(() -> service.request(user.getEmail()), () -> service.request(user.getEmail()));

        assertThat(countRows("PasswordResetToken", user.getId())).isEqualTo(1);
        verify(publisher, times(1)).publish(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void concurrentVerificationResendsIssueOneCredentialAndOneEmail() throws Exception {
        User user = inTransaction(() -> persistUser(false));
        CountDownLatch readsReached = new CountDownLatch(2);
        TransactionalEmailPublisher publisher = mock(TransactionalEmailPublisher.class);
        EmailVerificationService service = new EmailVerificationService(
                gateEmailReads(userRepository, readsReached), verificationTokens, new CredentialTokenService(),
                publisher, new TransactionalEmailTemplates(), properties());

        runConcurrently(() -> service.resend(user.getEmail()), () -> service.resend(user.getEmail()));

        assertThat(countRows("EmailVerificationToken", user.getId())).isEqualTo(1);
        verify(publisher, times(1)).publish(org.mockito.ArgumentMatchers.any());
    }

    private UserRepository gateEmailReads(UserRepository delegate, CountDownLatch readsReached) {
        return new UserRepository() {
            @Override public User save(User user) { return delegate.save(user); }
            @Override public User saveAndFlush(User user) { return delegate.saveAndFlush(user); }
            @Override public Optional<User> findById(UUID id) { return delegate.findById(id); }
            @Override public Optional<User> findByIdForUpdate(UUID id) { return delegate.findByIdForUpdate(id); }
            @Override public Optional<User> findByEmail(String email) {
                Optional<User> result = delegate.findByEmail(email);
                readsReached.countDown();
                awaitPeer(readsReached);
                return result;
            }
            @Override public Optional<User> findByEmailForUpdate(String email) {
                return delegate.findByEmailForUpdate(email);
            }
            @Override public boolean existsByEmail(String email) { return delegate.existsByEmail(email); }
            @Override public List<User> findAll() { return delegate.findAll(); }
        };
    }

    private void runConcurrently(Runnable firstCommand, Runnable secondCommand) throws Exception {
        CountDownLatch start = new CountDownLatch(1);
        try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
            Future<?> first = executor.submit(() -> execute(start, firstCommand));
            Future<?> second = executor.submit(() -> execute(start, secondCommand));
            start.countDown();
            first.get(10, TimeUnit.SECONDS);
            second.get(10, TimeUnit.SECONDS);
        }
    }

    private void execute(CountDownLatch start, Runnable command) {
        try {
            assertThat(start.await(5, TimeUnit.SECONDS)).isTrue();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(exception);
        }
        inTransaction(() -> {
            command.run();
            return null;
        });
    }

    private void awaitPeer(CountDownLatch latch) {
        try {
            assertThat(latch.await(5, TimeUnit.SECONDS)).isTrue();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(exception);
        }
    }

    private User persistUser(boolean verified) {
        User user = verified
                ? User.create(UUID.randomUUID() + "@example.com", "hash", "Verified User", UserRole.LEARNER)
                : User.createUnverified(UUID.randomUUID() + "@example.com", "hash", "Unverified User");
        entityManager.persist(user);
        entityManager.flush();
        return user;
    }

    private long countRows(String entityName, UUID userId) {
        return inTransaction(() -> entityManager.createQuery(
                        "select count(token) from " + entityName + " token where token.userId = :userId", Long.class)
                .setParameter("userId", userId)
                .getSingleResult());
    }

    private IdentityEmailProperties properties() {
        IdentityEmailProperties properties = new IdentityEmailProperties();
        properties.setResendCooldownSeconds(60);
        return properties;
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
}

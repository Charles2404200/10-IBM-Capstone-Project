package com.ibm.consulting.sim.identity.infrastructure;

import com.ibm.consulting.sim.identity.domain.User;
import com.ibm.consulting.sim.identity.domain.UserRepository;
import com.ibm.consulting.sim.identity.domain.UserRole;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Seeds exactly one {@code ADMINISTRATOR} account on application startup, sourced
 * from environment configuration ({@link AdminSeedProperties}), so a fresh
 * environment (e.g. a new Supabase project) never needs a manual SQL {@code UPDATE}
 * to bootstrap its first administrator.
 *
 * <p>Idempotent and self-healing by design:
 * <ul>
 *   <li>No account with that email yet → creates it as {@code ADMINISTRATOR}.</li>
 *   <li>Account already exists but isn't an administrator → promotes it in place
 *       (covers the case where the seed email collides with a self-registered learner).</li>
 *   <li>Account already exists and is already an administrator → no-op.</li>
 * </ul>
 * The seed password is never re-applied to an existing account — only used at
 * creation time — so rotating {@code ADMIN_PASSWORD} after the account already
 * exists has no effect (use {@code AdminUserController} / a password-change flow
 * for that instead). This avoids silently overwriting a password an operator may
 * have since changed through the product itself.
 */
@Component
public class AdminAccountSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(AdminAccountSeeder.class);

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final AdminSeedProperties seedProperties;

    public AdminAccountSeeder(UserRepository userRepository, PasswordEncoder passwordEncoder,
                               AdminSeedProperties seedProperties) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.seedProperties = seedProperties;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (!seedProperties.isConfigured()) {
            log.info("Admin seed skipped: ADMIN_EMAIL/ADMIN_PASSWORD not configured.");
            return;
        }

        String email = seedProperties.getEmail().trim().toLowerCase();
        userRepository.findByEmail(email).ifPresentOrElse(existing -> {
            if (existing.getRole() != UserRole.ADMINISTRATOR) {
                existing.changeRole(UserRole.ADMINISTRATOR);
                userRepository.save(existing);
                log.info("Admin seed: promoted existing account '{}' to ADMINISTRATOR.", email);
            } else {
                log.info("Admin seed: administrator '{}' already present, nothing to do.", email);
            }
        }, () -> {
            User admin = User.create(email, passwordEncoder.encode(seedProperties.getPassword()),
                    seedProperties.getDisplayName(), UserRole.ADMINISTRATOR);
            userRepository.save(admin);
            log.info("Admin seed: created initial administrator account '{}'.", email);
        });
    }
}

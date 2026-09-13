package com.ibm.consulting.sim.identity.application;

import com.ibm.consulting.sim.identity.domain.User;
import com.ibm.consulting.sim.identity.domain.UserDirectoryQuery;
import com.ibm.consulting.sim.identity.domain.UserRepository;
import com.ibm.consulting.sim.identity.domain.UserRole;
import com.ibm.consulting.sim.identity.domain.UserAlreadyExistsException;
import com.ibm.consulting.sim.shared.domain.NotFoundException;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.Caching;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;
import java.util.Locale;

import static com.ibm.consulting.sim.shared.config.CacheConfig.ADMIN_PLATFORM_OVERVIEW_CACHE;
import static com.ibm.consulting.sim.shared.config.CacheConfig.ADMIN_USER_DIRECTORY_CACHE;

/**
 * Administrative user management (§Enterprise Operations: authentication, roles,
 * auditability). Restricted to {@code ADMINISTRATOR} at the controller layer via
 * {@code @PreAuthorize} — this service assumes the caller has already been authorised.
 */
@Service
public class AdminUserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final EmailVerificationService emailVerificationService;

    public AdminUserService(UserRepository userRepository, PasswordEncoder passwordEncoder,
                            EmailVerificationService emailVerificationService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.emailVerificationService = emailVerificationService;
    }

    @Transactional(readOnly = true)
    @Cacheable(cacheNames = ADMIN_USER_DIRECTORY_CACHE, key = "#query.cacheKey()")
    public AdminUserPage listUsers(UserDirectoryQuery query) {
        return AdminUserPage.from(userRepository.findDirectory(query));
    }

    @Transactional
    @Caching(evict = {
            @CacheEvict(cacheNames = ADMIN_USER_DIRECTORY_CACHE, allEntries = true),
            @CacheEvict(cacheNames = ADMIN_PLATFORM_OVERVIEW_CACHE, allEntries = true)
    })
    public UserSummary createUser(String email, String password, String displayName, UserRole role,
                                  boolean skipEmailVerification) {
        String normalisedEmail = email.trim().toLowerCase(Locale.ROOT);
        if (userRepository.existsByEmail(normalisedEmail)) {
            throw new UserAlreadyExistsException(normalisedEmail);
        }

        User user = skipEmailVerification
                ? User.createVerifiedForTesting(normalisedEmail, passwordEncoder.encode(password), displayName.trim(), role)
                : User.createUnverified(normalisedEmail, passwordEncoder.encode(password), displayName.trim(), role);
        userRepository.save(user);

        if (!skipEmailVerification) {
            emailVerificationService.resend(user.getEmail());
        }
        return UserSummary.from(user);
    }

    @Transactional
    @Caching(evict = {
            @CacheEvict(cacheNames = ADMIN_USER_DIRECTORY_CACHE, allEntries = true),
            @CacheEvict(cacheNames = ADMIN_PLATFORM_OVERVIEW_CACHE, allEntries = true)
    })
    public UserSummary changeRole(UUID userId, UserRole newRole) {
        User user = findUser(userId);
        user.changeRole(newRole);
        userRepository.save(user);
        return UserSummary.from(user);
    }

    @Transactional
    @Caching(evict = {
            @CacheEvict(cacheNames = ADMIN_USER_DIRECTORY_CACHE, allEntries = true),
            @CacheEvict(cacheNames = ADMIN_PLATFORM_OVERVIEW_CACHE, allEntries = true)
    })
    public UserSummary deactivate(UUID userId) {
        User user = findUser(userId);
        user.deactivate();
        userRepository.save(user);
        return UserSummary.from(user);
    }

    @Transactional
    @Caching(evict = {
            @CacheEvict(cacheNames = ADMIN_USER_DIRECTORY_CACHE, allEntries = true),
            @CacheEvict(cacheNames = ADMIN_PLATFORM_OVERVIEW_CACHE, allEntries = true)
    })
    public UserSummary reactivate(UUID userId) {
        User user = findUser(userId);
        user.reactivate();
        userRepository.save(user);
        return UserSummary.from(user);
    }

    @Transactional
    @Caching(evict = {
            @CacheEvict(cacheNames = ADMIN_USER_DIRECTORY_CACHE, allEntries = true),
            @CacheEvict(cacheNames = ADMIN_PLATFORM_OVERVIEW_CACHE, allEntries = true)
    })
    public void deleteUser(UUID userId, UUID requestingUserId) {
        if (userId.equals(requestingUserId)) {
            throw new IllegalArgumentException("You cannot delete your own account.");
        }

        User user = findUser(userId);
        if (user.getRole() == UserRole.ADMINISTRATOR && user.isActive()
                && userRepository.countByRoleAndActive(UserRole.ADMINISTRATOR, true) <= 1) {
            throw new IllegalArgumentException("The last active administrator cannot be deleted.");
        }
        userRepository.delete(user);
    }

    private User findUser(UUID userId) {
        return userRepository.findById(userId).orElseThrow(() -> new NotFoundException("User", userId));
    }
}

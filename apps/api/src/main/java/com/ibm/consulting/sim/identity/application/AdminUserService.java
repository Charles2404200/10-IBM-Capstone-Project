package com.ibm.consulting.sim.identity.application;

import com.ibm.consulting.sim.identity.domain.User;
import com.ibm.consulting.sim.identity.domain.UserDirectoryQuery;
import com.ibm.consulting.sim.identity.domain.UserRepository;
import com.ibm.consulting.sim.identity.domain.UserRole;
import com.ibm.consulting.sim.shared.domain.NotFoundException;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.Caching;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

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

    public AdminUserService(UserRepository userRepository) {
        this.userRepository = userRepository;
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

    private User findUser(UUID userId) {
        return userRepository.findById(userId).orElseThrow(() -> new NotFoundException("User", userId));
    }
}

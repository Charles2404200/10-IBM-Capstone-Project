package com.ibm.consulting.sim.identity.application;

import java.util.List;
import java.util.Locale;
import java.util.UUID;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ibm.consulting.sim.identity.domain.User;
import com.ibm.consulting.sim.identity.domain.UserAlreadyExistsException;
import com.ibm.consulting.sim.identity.domain.UserRepository;
import com.ibm.consulting.sim.identity.domain.UserRole;
import com.ibm.consulting.sim.shared.domain.NotFoundException;
import com.ibm.consulting.sim.shared.infrastructure.observability.AuditAction;
import com.ibm.consulting.sim.shared.infrastructure.observability.AuditLogger;

/**
 * Administrative user management (§Enterprise Operations: authentication, roles,
 * auditability). Restricted to {@code ADMINISTRATOR} at the controller layer via
 * {@code @PreAuthorize} — this service assumes the caller has already been authorised.
 */
@Service
public class AdminUserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final PasswordResetService passwordResetService;
    private final AuditLogger auditLogger;

    public AdminUserService(UserRepository userRepository, PasswordEncoder passwordEncoder, PasswordResetService passwordResetService, AuditLogger auditLogger) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.passwordResetService = passwordResetService;
        this.auditLogger = auditLogger;
    }

    @Transactional(readOnly = true)
    public List<UserSummary> listUsers() {
        return userRepository.findAll().stream().map(UserSummary::from).toList();
    }

    @Transactional
    public UserSummary changeRole(UUID userId, UserRole newRole) {
        User user = findUser(userId);
        user.changeRole(newRole);
        userRepository.save(user);
        auditLogger.recordAdmin(AuditAction.ADMIN_USER_ROLE_CHANGED, "USER", userId.toString(), newRole.name());
        return UserSummary.from(user);
    }

    @Transactional
    public UserSummary deactivate(UUID userId) {
        User user = findUser(userId);
        user.deactivate();
        userRepository.save(user);
        auditLogger.recordAdmin(AuditAction.ADMIN_USER_DEACTIVATED, "USER", userId.toString());
        return UserSummary.from(user);
    }

    @Transactional
    public UserSummary reactivate(UUID userId) {
        User user = findUser(userId);
        user.reactivate();
        userRepository.save(user);
        auditLogger.recordAdmin(AuditAction.ADMIN_USER_REACTIVATED, "USER", userId.toString());
        return UserSummary.from(user);
    }

    @Transactional
    public UserSummary createUser(String email, String displayName, UserRole role) {
        String normalisedEmail = email.trim().toLowerCase(Locale.ROOT);
        if (userRepository.existsByEmail(normalisedEmail)) {
            throw new UserAlreadyExistsException(normalisedEmail);
        }

        // placeholder password unknown to both admin and user
        // user must reset it via email before first login
        String placeholderPassword = passwordEncoder.encode(UUID.randomUUID().toString());

        User user = User.create(normalisedEmail, placeholderPassword, displayName.trim(), role);
        userRepository.save(user);

        passwordResetService.issueSetUpLink(user);

        // logs admin account creation
        auditLogger.recordAdmin(AuditAction.ADMIN_USER_CREATED, "USER", user.getId().toString(), normalisedEmail);

        return UserSummary.from(user);
    }

    private User findUser(UUID userId) {
        return userRepository.findById(userId).orElseThrow(() -> new NotFoundException("User", userId));
    }
}

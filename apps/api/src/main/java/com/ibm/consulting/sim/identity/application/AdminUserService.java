package com.ibm.consulting.sim.identity.application;

import com.ibm.consulting.sim.identity.domain.User;
import com.ibm.consulting.sim.identity.domain.UserRepository;
import com.ibm.consulting.sim.identity.domain.UserRole;
import com.ibm.consulting.sim.shared.domain.NotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

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
    public List<UserSummary> listUsers() {
        return userRepository.findAll().stream().map(UserSummary::from).toList();
    }

    @Transactional
    public UserSummary changeRole(UUID userId, UserRole newRole) {
        User user = findUser(userId);
        user.changeRole(newRole);
        userRepository.save(user);
        return UserSummary.from(user);
    }

    @Transactional
    public UserSummary deactivate(UUID userId) {
        User user = findUser(userId);
        user.deactivate();
        userRepository.save(user);
        return UserSummary.from(user);
    }

    @Transactional
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

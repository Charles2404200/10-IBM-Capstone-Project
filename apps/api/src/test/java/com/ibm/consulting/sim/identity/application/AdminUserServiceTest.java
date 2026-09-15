package com.ibm.consulting.sim.identity.application;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.ibm.consulting.sim.identity.domain.User;
import com.ibm.consulting.sim.identity.domain.UserRepository;
import com.ibm.consulting.sim.identity.domain.UserRole;
import com.ibm.consulting.sim.shared.infrastructure.observability.AuditAction;
import com.ibm.consulting.sim.shared.infrastructure.observability.AuditLogger;

@ExtendWith(MockitoExtension.class)
class AdminUserServiceTest {
    @Mock UserRepository userRepository;
    @Mock PasswordEncoder passwordEncoder;
    @Mock PasswordResetService passwordResetService;
    @Mock AuditLogger auditLogger;

    @InjectMocks AdminUserService service;

    // audit logging - change user role
    @Test
    void logsRoleChanged() {
        UUID userId = UUID.randomUUID();

        // mock existing user
        User user = mock(User.class);
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));

        // change user role
        service.changeRole(userId, UserRole.ADMINISTRATOR);
        verify(auditLogger).recordAdmin(
            eq(AuditAction.ADMIN_USER_ROLE_CHANGED),
            eq("USER"),
            eq(userId.toString()),
            eq(UserRole.ADMINISTRATOR.name()));
    }

    // audit logging - deactivate user
    @Test
    void logsUserDeactivated() {
        UUID userId = UUID.randomUUID();
        User user = mock(User.class);
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));

        // deactivate user
        service.deactivate(userId);
        verify(auditLogger).recordAdmin(
            eq(AuditAction.ADMIN_USER_DEACTIVATED),
            eq("USER"),
            eq(userId.toString()));
    }

    // audit logging - reactivate user
    @Test
    void logsUserReactivated() {
        UUID userId = UUID.randomUUID();
        User user = mock(User.class);
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));

        // reactivate user
        service.reactivate(userId);
        verify(auditLogger).recordAdmin(
            eq(AuditAction.ADMIN_USER_REACTIVATED),
            eq("USER"),
            eq(userId.toString()));
    }
}
package com.ibm.consulting.sim.identity.application;

import com.ibm.consulting.sim.identity.domain.User;
import com.ibm.consulting.sim.identity.domain.UserRole;

import java.util.UUID;

public record UserSummary(UUID id, String email, String displayName, UserRole role, boolean active) {

    public static UserSummary from(User user) {
        return new UserSummary(user.getId(), user.getEmail(), user.getDisplayName(), user.getRole(), user.isActive());
    }
}

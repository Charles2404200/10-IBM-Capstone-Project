package com.ibm.consulting.sim.identity.domain;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class UserOnboardingTest {

    @Test
    void only_newly_registered_users_require_onboarding() {
        User existingUser = User.create("existing@example.com", "hash", "Existing", UserRole.LEARNER);
        User newUser = User.createUnverified("new@example.com", "hash", "New learner");

        assertThat(existingUser.requiresOnboarding()).isFalse();
        assertThat(newUser.requiresOnboarding()).isTrue();

        newUser.completeOnboarding(Instant.now());

        assertThat(newUser.requiresOnboarding()).isFalse();
    }
}

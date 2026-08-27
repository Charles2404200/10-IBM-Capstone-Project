package com.ibm.consulting.sim.identity.application;

import com.ibm.consulting.sim.identity.domain.User;
import com.ibm.consulting.sim.identity.domain.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Service
public class UserOnboardingService {

    private final UserRepository userRepository;

    public UserOnboardingService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    /** Idempotently records the learner's first product orientation. */
    @Transactional
    public void complete(User authenticatedUser) {
        User user = userRepository.findById(authenticatedUser.getId())
                .orElseThrow(() -> new IllegalStateException("Authenticated user no longer exists"));
        if (!user.requiresOnboarding()) {
            return;
        }
        user.completeOnboarding(Instant.now());
        userRepository.save(user);
    }
}

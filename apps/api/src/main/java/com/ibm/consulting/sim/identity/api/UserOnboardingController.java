package com.ibm.consulting.sim.identity.api;

import com.ibm.consulting.sim.identity.application.UserOnboardingService;
import com.ibm.consulting.sim.identity.domain.User;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/users/me/onboarding")
public class UserOnboardingController {

    private final UserOnboardingService onboardingService;

    public UserOnboardingController(UserOnboardingService onboardingService) {
        this.onboardingService = onboardingService;
    }

    @PostMapping("/complete")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void complete(@AuthenticationPrincipal User user) {
        onboardingService.complete(user);
    }
}

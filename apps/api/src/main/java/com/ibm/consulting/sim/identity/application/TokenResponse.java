package com.ibm.consulting.sim.identity.application;

public record TokenResponse(
        String accessToken,
        String userId,
        String displayName,
        String role,
        boolean onboardingRequired) {}

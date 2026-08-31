package com.ibm.consulting.sim.identity.application;

/** Registration deliberately does not contain a bearer token. */
public record RegistrationResponse(String email, boolean emailVerificationRequired) {}

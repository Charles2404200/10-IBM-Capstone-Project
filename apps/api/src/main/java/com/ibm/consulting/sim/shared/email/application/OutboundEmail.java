package com.ibm.consulting.sim.shared.email.application;

/** Provider-neutral transactional email payload. */
public record OutboundEmail(String recipient, String subject, String html, String text) {}

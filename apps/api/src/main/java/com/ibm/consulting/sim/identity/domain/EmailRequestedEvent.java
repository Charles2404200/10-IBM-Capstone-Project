package com.ibm.consulting.sim.identity.domain;

import java.util.UUID;

public record EmailRequestedEvent(
        MailBody mailBody,
        String message,
        UUID userId
) {}
package com.ibm.consulting.sim.admin.domain;

import java.time.Instant;
import java.util.UUID;

public record NotificationReadReceipt(UUID userId, Instant readAt) {
}

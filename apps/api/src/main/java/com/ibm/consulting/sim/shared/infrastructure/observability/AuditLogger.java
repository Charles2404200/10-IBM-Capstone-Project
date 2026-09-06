package com.ibm.consulting.sim.shared.infrastructure.observability;

import java.time.Instant;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import com.ibm.consulting.sim.identity.domain.User;

@Component
public class AuditLogger {
    private static final Logger log = LoggerFactory.getLogger(AuditLogger.class);

    // overloaded method that doesn't require details
    public void recordAdmin(AuditAction action, String targetType, String targetId) {
        recordAdmin(action, targetType, targetId, null);
    }

    // for admin actions
    public void recordAdmin(AuditAction action, String targetType, String targetId, String details) {
        var event = log.atInfo()
                .addKeyValue("event", action.name())
                .addKeyValue("adminUUID", currAdminUUID())
                .addKeyValue("targetType", targetType)
                .addKeyValue("targetUUID", targetId)
                .addKeyValue("timestamp", Instant.now().toString());
        if (details != null) {
            event.addKeyValue("details", details);
        }
        event.log("Admin audit event logged");
    }

    // gets current user UUID - preauthorised to be admin at controller layer for all calls to this service
    private UUID currAdminUUID() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof User user) {
            return user.getId();
        }
        return null;
    }
}
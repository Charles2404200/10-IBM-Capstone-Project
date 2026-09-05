package com.ibm.consulting.sim.shared.infrastructure.observability;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;

import com.ibm.consulting.sim.identity.domain.User;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AuditLoggerTest {

    private final AuditLogger auditLogger = new AuditLogger();
    private final ListAppender<ILoggingEvent> appender = new ListAppender<>();
    private Logger targetLogger;

    private final UUID adminUUID = UUID.randomUUID();

    @BeforeEach
    void attachAppender() {
        // attach appender to AuditLogger to check the logged event
        targetLogger = (Logger) LoggerFactory.getLogger(AuditLogger.class);
        appender.start();
        targetLogger.addAppender(appender);

        // provides authenticated admin
        User admin = mock(User.class);
        when(admin.getId()).thenReturn(adminUUID);
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(admin, null));
    }

    // ensures auth from each test does not affect other tests
    @AfterEach
    void detachAppender() {
        targetLogger.detachAppender(appender);
        SecurityContextHolder.clearContext();
    }

    // check full audit event
    @Test
    void recordsCompleteAuditEvent() {
        // get time before and after logger call
        Instant before = Instant.now();
        auditLogger.recordAdmin(AuditAction.ADMIN_USER_DEACTIVATED, "USER", "user001", "details");
        Instant after = Instant.now();

        assertThat(appender.list).hasSize(1);
        ILoggingEvent event = appender.list.get(0);
        
        // check for expected audit values
        assertThat(event.getKeyValuePairs())
            .anyMatch(kv -> kv.key.equals("event") && kv.value.equals("ADMIN_USER_DEACTIVATED"))
            .anyMatch(kv -> kv.key.equals("adminUUID") && kv.value.equals(adminUUID))
            .anyMatch(kv -> kv.key.equals("targetType") && kv.value.equals("USER"))
            .anyMatch(kv -> kv.key.equals("targetUUID") && kv.value.equals("user001"))
            .anyMatch(kv -> kv.key.equals("details") && kv.value.equals("details"));

        // check timestamp is within before and after logger call
        Object timestampValue = event.getKeyValuePairs().stream()
            .filter(kv -> kv.key.equals("timestamp"))
            .map(kv -> kv.value)
            .findFirst()
            .orElse(null);

        assertThat(timestampValue).isNotNull();
        Instant timestamp = Instant.parse(timestampValue.toString());
        assertThat(timestamp).isBetween(before, after);
    }

    // check audit records successfully, even when details field is not provided
    @Test
    void omitsDetailsWhenNotProvided() {
        auditLogger.recordAdmin(AuditAction.ADMIN_USER_DEACTIVATED, "USER", "user001");
        ILoggingEvent event = appender.list.get(0);
        assertThat(event.getKeyValuePairs()).noneMatch(kv -> kv.key.equals("details"));
    }

    // ensures logs are info level
    @Test
    void logsAtInfoLevel() {
        auditLogger.recordAdmin(AuditAction.ADMIN_SCENARIO_PUBLISHED, "SCENARIO", "scenario001");
        assertThat(appender.list.get(0).getLevel().toString()).isEqualTo("INFO");
    }
}
package com.ibm.consulting.sim.admin.application;

import com.ibm.consulting.sim.admin.domain.NotificationPriority;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.Locale;
import java.util.Map;

/** Low-cardinality notification metrics; identifiers are intentionally never tags. */
@Component
public class NotificationMetrics {

    private final Map<NotificationPriority, Counter> websocketSent =
            new EnumMap<>(NotificationPriority.class);
    private final Map<NotificationPriority, Counter> websocketFailed =
            new EnumMap<>(NotificationPriority.class);
    private final Counter readsMarked;
    private final Counter readsAlreadyMarked;

    public NotificationMetrics(MeterRegistry registry) {
        for (NotificationPriority priority : NotificationPriority.values()) {
            String label = priority.name().toLowerCase(Locale.ROOT);
            websocketSent.put(priority, Counter.builder("consulting.notification.websocket.sent")
                    .description("Best-effort notification WebSocket hints sent")
                    .tag("priority", label)
                    .register(registry));
            websocketFailed.put(priority, Counter.builder("consulting.notification.websocket.failed")
                    .description("Best-effort notification WebSocket hint failures")
                    .tag("priority", label)
                    .register(registry));
        }
        readsMarked = Counter.builder("consulting.notification.read.marked")
                .description("Notification read receipts newly created")
                .tag("result", "created")
                .register(registry);
        readsAlreadyMarked = Counter.builder("consulting.notification.read.marked")
                .description("Idempotent notification read requests for existing receipts")
                .tag("result", "already_read")
                .register(registry);
    }

    public void recordWebSocketSent(NotificationPriority priority) {
        websocketSent.get(NotificationPriority.normalize(priority)).increment();
    }

    public void recordWebSocketFailure(NotificationPriority priority) {
        websocketFailed.get(NotificationPriority.normalize(priority)).increment();
    }

    public void recordReadMarked(boolean created) {
        (created ? readsMarked : readsAlreadyMarked).increment();
    }
}

package com.ibm.consulting.sim.shared.application.outbox;

import com.ibm.consulting.sim.shared.domain.outbox.EventPriority;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.Locale;
import java.util.Map;

/** Low-cardinality dispatcher metrics partitioned only by controlled priority. */
@Component
public class OutboxMetrics {

    private final Map<EventPriority, Counter> successes = new EnumMap<>(EventPriority.class);
    private final Map<EventPriority, Counter> failures = new EnumMap<>(EventPriority.class);
    private final Map<EventPriority, Counter> retries = new EnumMap<>(EventPriority.class);

    public OutboxMetrics(MeterRegistry registry) {
        for (EventPriority priority : EventPriority.values()) {
            String label = priority.name().toLowerCase(Locale.ROOT);
            successes.put(priority, counter(registry, "consulting.outbox.dispatch.success", label));
            failures.put(priority, counter(registry, "consulting.outbox.dispatch.failure", label));
            retries.put(priority, counter(registry, "consulting.outbox.retry", label));
        }
    }

    private Counter counter(MeterRegistry registry, String name, String priority) {
        return Counter.builder(name)
                .description("Priority-aware transactional outbox activity")
                .tag("priority", priority)
                .register(registry);
    }

    public void recordSuccess(EventPriority priority) {
        successes.get(priority).increment();
    }

    public void recordFailure(EventPriority priority) {
        failures.get(priority).increment();
    }

    public void recordRetry(EventPriority priority) {
        retries.get(priority).increment();
    }
}

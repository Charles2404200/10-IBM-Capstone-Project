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

    // Priority is a bounded enum, so it is safe to use as a metric tag without
    // creating unbounded time-series cardinality.
    private final Map<EventPriority, Counter> successes = new EnumMap<>(EventPriority.class);
    private final Map<EventPriority, Counter> failures = new EnumMap<>(EventPriority.class);
    private final Map<EventPriority, Counter> retries = new EnumMap<>(EventPriority.class);

    // Lifecycle counters expose throughput and maintenance activity without
    // tagging event IDs, claim tokens, topics, or other high-cardinality data.
    private final Counter claimed;
    private final Counter recovered;
    private final Counter cleaned;

    // A zero-row ownership update means a stale worker attempted to complete a
    // lease that was already recovered or reassigned. The database remains safe,
    // but an increase can indicate workers exceeding the configured lease time.
    private final Counter ownershipConflicts;

    // Counts database failures after Kafka has returned a result. The row is
    // deliberately left PROCESSING for lease recovery; after a successful Kafka
    // send this failure window can produce a duplicate delivery.
    private final Counter completionStateFailures;

    // Counts rows that were claimed successfully but could not subsequently be
    // loaded. Only that row is deferred to stale recovery; the rest of the batch
    // continues publishing.
    private final Counter claimedEventLoadFailures;

    public OutboxMetrics(MeterRegistry registry) {
        for (EventPriority priority : EventPriority.values()) {
            String label = priority.name().toLowerCase(Locale.ROOT);
            successes.put(priority, priorityCounter(registry, "consulting.outbox.dispatch.success", label));
            failures.put(priority, priorityCounter(registry, "consulting.outbox.dispatch.failure", label));
            retries.put(priority, priorityCounter(registry, "consulting.outbox.retry", label));
        }
        claimed = counter(registry, "consulting.outbox.claimed",
                "Outbox rows successfully claimed for dispatch");
        recovered = counter(registry, "consulting.outbox.recovered",
                "Expired PROCESSING leases returned to PENDING");
        cleaned = counter(registry, "consulting.outbox.cleaned",
                "Expired PUBLISHED outbox rows deleted");
        ownershipConflicts = counter(registry, "consulting.outbox.ownership.conflict",
                "Stale workers rejected by claim-token ownership checks");
        completionStateFailures = counter(registry, "consulting.outbox.completion.state.failure",
                "Database failures while completing Kafka publication state");
        claimedEventLoadFailures = counter(registry, "consulting.outbox.claimed.load.failure",
                "Claimed rows that could not be loaded for publication");
    }

    private Counter priorityCounter(MeterRegistry registry, String name, String priority) {
        return Counter.builder(name)
                .description("Priority-aware transactional outbox activity")
                .tag("priority", priority)
                .register(registry);
    }

    private Counter counter(MeterRegistry registry, String name, String description) {
        return Counter.builder(name)
                .description(description)
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

    public void recordClaimed(int count) {
        incrementBy(claimed, count);
    }

    public void recordRecovered(int count) {
        incrementBy(recovered, count);
    }

    public void recordCleaned(int count) {
        incrementBy(cleaned, count);
    }

    public void recordOwnershipConflict() {
        ownershipConflicts.increment();
    }

    public void recordCompletionStateFailure() {
        completionStateFailures.increment();
    }

    public void recordClaimedEventLoadFailure() {
        claimedEventLoadFailures.increment();
    }

    private void incrementBy(Counter counter, int count) {
        if (count > 0) {
            counter.increment(count);
        }
    }
}

package com.ibm.consulting.sim.shared.application.kafka;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

/** Low-cardinality metrics for durable consumer idempotency. */
@Component
public class KafkaInboxMetrics {

    // These counters intentionally have no event, topic, or consumer-group tags;
    // those values are unbounded and would make the metrics backend unsafe at scale.
    private final Counter processed;
    private final Counter duplicates;

    public KafkaInboxMetrics(MeterRegistry registry) {
        processed = Counter.builder("consulting.kafka.inbox.processed")
                .description("Kafka events committed with their inbox claim")
                .register(registry);
        duplicates = Counter.builder("consulting.kafka.inbox.duplicate")
                .description("Duplicate Kafka deliveries ignored by the durable inbox")
                .register(registry);
    }

    public void recordProcessed() {
        processed.increment();
    }

    public void recordDuplicate() {
        duplicates.increment();
    }
}

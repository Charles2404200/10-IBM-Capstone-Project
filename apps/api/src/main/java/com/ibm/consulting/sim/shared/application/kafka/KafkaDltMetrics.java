package com.ibm.consulting.sim.shared.application.kafka;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

/** Low-cardinality operational metrics for the terminal Kafka DLT consumer. */
@Component
public class KafkaDltMetrics {

    private final Counter received;
    private final Counter handlingFailures;

    public KafkaDltMetrics(MeterRegistry registry) {
        received = Counter.builder("consulting.kafka.dlt.received")
                .description("Kafka records received by the terminal DLT consumer")
                .register(registry);
        handlingFailures = Counter.builder("consulting.kafka.dlt.handling.failure")
                .description("DLT records that the terminal handler could not process")
                .register(registry);
    }

    public void recordReceived() {
        received.increment();
    }

    public void recordHandlingFailure() {
        handlingFailures.increment();
    }
}

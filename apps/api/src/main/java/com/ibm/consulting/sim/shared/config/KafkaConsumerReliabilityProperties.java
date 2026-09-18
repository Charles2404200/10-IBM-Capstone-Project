package com.ibm.consulting.sim.shared.config;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import org.hibernate.validator.constraints.time.DurationMin;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

/** Bounded retry policy for EventEnvelope listeners. */
@Validated
@ConfigurationProperties(prefix = "app.kafka.consumer")
public record KafkaConsumerReliabilityProperties(
        @DefaultValue("2s") @NotNull @DurationMin Duration retryBackoff,
        @DefaultValue("3") @PositiveOrZero long maxRetries
) {
}

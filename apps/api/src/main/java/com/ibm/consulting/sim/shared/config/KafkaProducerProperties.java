package com.ibm.consulting.sim.shared.config;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import org.hibernate.validator.constraints.time.DurationMin;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

/** Strongly typed reliability settings for the shared Kafka producer. */
@Validated
@ConfigurationProperties(prefix = "app.kafka.producer")
public record KafkaProducerProperties(
        @DefaultValue("120s") @NotNull @DurationMin(millis = 1) Duration deliveryTimeout,
        @DefaultValue("30s") @NotNull @DurationMin(millis = 1) Duration requestTimeout,
        @DefaultValue("500ms") @NotNull @DurationMin Duration retryBackoff,
        @DefaultValue("2147483647") @PositiveOrZero int retries,
        @DefaultValue("5") @Min(1) @Max(5) int parallelRequests
) {
}

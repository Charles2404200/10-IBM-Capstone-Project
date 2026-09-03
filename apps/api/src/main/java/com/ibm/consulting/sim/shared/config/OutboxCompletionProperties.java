package com.ibm.consulting.sim.shared.config;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import org.hibernate.validator.constraints.time.DurationMin;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

/** Bounded executor and shutdown policy for outbox completion transitions. */
@Validated
@ConfigurationProperties(prefix = "app.async.outbox-completion")
public record OutboxCompletionProperties(
        @DefaultValue("8") @Positive int poolSize,
        @DefaultValue("500") @Positive int queueCapacity,
        @DefaultValue("15s") @NotNull @DurationMin(seconds = 1) Duration shutdownTimeout
) {
}

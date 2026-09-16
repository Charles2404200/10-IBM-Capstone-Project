package com.ibm.consulting.sim.shared.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import org.hibernate.validator.constraints.time.DurationMin;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

/** Operational limits for claiming, retrying, recovering, and retaining outbox rows. */
@Validated
@ConfigurationProperties(prefix = "app.kafka.outbox")
public record OutboxProperties(
        @DefaultValue("200ms") @NotNull @DurationMin(millis = 1) Duration pollDelay,
        @DefaultValue("100") @Positive int batchSize,
        @DefaultValue("30s") @NotNull @DurationMin(millis = 1) Duration recoveryDelay,
        @DefaultValue("30s") @NotNull @DurationMin(millis = 1) Duration recoverySafetyMargin,
        @DefaultValue("10") @Positive int maxAttempts,
        @DefaultValue("0 */10 * * * *") @NotBlank String cleanupCron,
        @DefaultValue("2d") @NotNull @DurationMin(millis = 1) Duration retention,
        @DefaultValue("10000") @Positive int cleanupBatchSize,
        @Valid @DefaultValue Retry retry
) {
    public record Retry(
            @DefaultValue("1s") @NotNull @DurationMin(millis = 1) Duration initialDelay,
            @DefaultValue("5m") @NotNull @DurationMin(millis = 1) Duration maxDelay,
            @DefaultValue("2.0") @DecimalMin("1.0") double multiplier,
            @DefaultValue("0.2") @DecimalMin("0.0") @DecimalMax("1.0") double jitterRatio
    ) {
        public Retry {
            if (initialDelay != null && maxDelay != null && maxDelay.compareTo(initialDelay) < 0) {
                throw new IllegalArgumentException("Outbox retry maxDelay must not be less than initialDelay");
            }
        }
    }
}

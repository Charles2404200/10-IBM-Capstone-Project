package com.ibm.consulting.sim.shared.config;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import org.hibernate.validator.constraints.time.DurationMin;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

/** Retention settings for durable Kafka consumer deduplication records. */
@Validated
@ConfigurationProperties(prefix = "app.kafka.inbox")
public record KafkaInboxProperties(
        @DefaultValue("14d") @NotNull @DurationMin(millis = 1) Duration retention,
        @DefaultValue("10000") @Positive int cleanupBatchSize,
        @DefaultValue("0 15 * * * *") @NotBlank String cleanupCron
) {
}

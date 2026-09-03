package com.ibm.consulting.sim.shared.config;

import com.ibm.consulting.sim.admin.infrastructure.NotificationKafkaProperties;

import java.time.Duration;

/** Shared strongly typed fixtures for Kafka/outbox unit tests. */
public final class TestKafkaProperties {

    private TestKafkaProperties() {
    }

    public static KafkaProducerProperties producer() {
        return new KafkaProducerProperties(
                Duration.ofSeconds(120),
                Duration.ofSeconds(30),
                Duration.ofMillis(500),
                Integer.MAX_VALUE,
                5
        );
    }

    public static KafkaConsumerReliabilityProperties consumer() {
        return new KafkaConsumerReliabilityProperties(Duration.ofSeconds(2), 3);
    }

    public static NotificationKafkaProperties notifications() {
        return new NotificationKafkaProperties(
                new KafkaTopicProperties("notifications", 1, 1),
                new KafkaConsumerProperties("notification-listener", 1),
                new KafkaDltProperties(
                        "notifications.DLT",
                        new KafkaConsumerProperties("notification-dlt-monitor", 1)
                )
        );
    }

    public static OutboxProperties outbox() {
        return outbox(100, 10, Duration.ofDays(2), 10_000, retry());
    }

    public static OutboxProperties outbox(int batchSize) {
        return outbox(batchSize, 10, Duration.ofDays(2), 10_000, retry());
    }

    public static OutboxProperties outbox(
            int batchSize,
            int maxAttempts,
            Duration retention,
            int cleanupBatchSize,
            OutboxProperties.Retry retry) {
        return new OutboxProperties(
                Duration.ofMillis(200),
                batchSize,
                Duration.ofSeconds(30),
                Duration.ofSeconds(30),
                maxAttempts,
                "0 */10 * * * *",
                retention,
                cleanupBatchSize,
                retry
        );
    }

    public static OutboxProperties.Retry retry() {
        return retry(Duration.ofSeconds(1), Duration.ofMinutes(5), 2.0, 0.2);
    }

    public static OutboxProperties.Retry retry(
            Duration initial,
            Duration maximum,
            double multiplier,
            double jitter) {
        return new OutboxProperties.Retry(initial, maximum, multiplier, jitter);
    }

    public static KafkaInboxProperties inbox() {
        return new KafkaInboxProperties(Duration.ofDays(14), 1_000, "0 15 * * * *");
    }
}

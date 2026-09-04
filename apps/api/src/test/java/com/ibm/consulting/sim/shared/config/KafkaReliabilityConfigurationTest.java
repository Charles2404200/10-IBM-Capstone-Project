package com.ibm.consulting.sim.shared.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.backoff.BackOffExecution;
import org.springframework.util.backoff.FixedBackOff;

import static org.assertj.core.api.Assertions.assertThat;

class KafkaReliabilityConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(ReliabilityPropertiesConfiguration.class);

    @Test
    void producerRejectsNonPositiveDeliveryTimeoutAtStartup() {
        contextRunner
                .withPropertyValues("app.kafka.producer.delivery-timeout=0ms")
                .run(context -> assertThat(context).hasFailed());
    }

    @Test
    void producerRejectsUnsafeParallelRequestCountAtStartup() {
        contextRunner
                .withPropertyValues("app.kafka.producer.parallel-requests=6")
                .run(context -> assertThat(context).hasFailed());
    }

    @Test
    void consumerRejectsNegativeRetryConfigurationAtStartup() {
        contextRunner
                .withPropertyValues("app.kafka.consumer.max-retries=-1")
                .run(context -> assertThat(context).hasFailed());
        contextRunner
                .withPropertyValues("app.kafka.consumer.retry-backoff=-1ms")
                .run(context -> assertThat(context).hasFailed());
    }

    @Test
    void consumerRetryEnvironmentStyleOverridesBindToTypedProperties() {
        contextRunner
                .withPropertyValues(
                        "app.kafka.consumer.retry-backoff=750ms",
                        "app.kafka.consumer.max-retries=5")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    KafkaConsumerReliabilityProperties properties =
                            context.getBean(KafkaConsumerReliabilityProperties.class);
                    assertThat(properties.retryBackoff()).isEqualTo(java.time.Duration.ofMillis(750));
                    assertThat(properties.maxRetries()).isEqualTo(5);
                });
    }

    @Test
    void zeroConsumerRetriesIsAValidFailFastPolicy() {
        contextRunner
                .withPropertyValues("app.kafka.consumer.max-retries=0")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context.getBean(KafkaConsumerReliabilityProperties.class).maxRetries())
                            .isZero();
                    KafkaConsumerReliabilityProperties properties =
                            context.getBean(KafkaConsumerReliabilityProperties.class);
                    FixedBackOff backOff = new FixedBackOff(
                            properties.retryBackoff().toMillis(), properties.maxRetries());
                    assertThat(backOff.start().nextBackOff()).isEqualTo(BackOffExecution.STOP);
                });
    }

    @Test
    void outboxRejectsNonPositiveMaximumAttemptsAtStartup() {
        contextRunner
                .withPropertyValues("app.kafka.outbox.max-attempts=0")
                .run(context -> assertThat(context).hasFailed());
    }

    @Test
    void validDefaultsBindAsDurations() {
        contextRunner.run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context.getBean(KafkaProducerProperties.class).deliveryTimeout())
                    .hasSeconds(120);
            assertThat(context.getBean(KafkaConsumerReliabilityProperties.class).retryBackoff())
                    .hasSeconds(2);
            assertThat(context.getBean(KafkaConsumerReliabilityProperties.class).maxRetries())
                    .isEqualTo(3);
            assertThat(context.getBean(OutboxProperties.class).retention())
                    .isEqualTo(java.time.Duration.ofDays(2));
        });
    }

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties({
            KafkaProducerProperties.class,
            KafkaConsumerReliabilityProperties.class,
            OutboxProperties.class,
            KafkaInboxProperties.class
    })
    static class ReliabilityPropertiesConfiguration {
    }
}

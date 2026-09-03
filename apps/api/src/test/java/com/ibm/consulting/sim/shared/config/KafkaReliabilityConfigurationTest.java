package com.ibm.consulting.sim.shared.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

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

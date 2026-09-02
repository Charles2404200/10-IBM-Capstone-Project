package com.ibm.consulting.sim.shared.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.kafka.KafkaProperties;
import org.springframework.boot.ssl.SslBundles;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;

class KafkaReliabilityConfigurationTest {

    // Configuration bean tests intentionally bypass a Spring context so each
    // invalid deployment value can be verified as a deterministic startup error.

    @Test
    void producerRejectsNonPositiveDeliveryTimeoutAtStartup() {
        KafkaProducerConfig config = validProducerConfig();
        ReflectionTestUtils.setField(config, "deliveryTimeoutMs", 0);

        assertThrows(
                IllegalArgumentException.class,
                () -> config.producerFactory(
                        new KafkaProperties(),
                        mock(SslBundles.class))
        );
    }

    @Test
    void producerRejectsNegativeRetryConfigurationAtStartup() {
        KafkaProducerConfig config = validProducerConfig();
        ReflectionTestUtils.setField(config, "retryBackoffMs", -1L);

        assertThrows(
                IllegalArgumentException.class,
                () -> config.producerFactory(
                        new KafkaProperties(),
                        mock(SslBundles.class))
        );
    }

    @Test
    @SuppressWarnings("unchecked")
    void consumerRejectsNegativeRetryConfigurationAtStartup() {
        KafkaConsumerConfig config = new KafkaConsumerConfig();
        ReflectionTestUtils.setField(config, "retryBackoffMs", -1L);
        ReflectionTestUtils.setField(config, "maxRetries", 3L);

        assertThrows(
                IllegalArgumentException.class,
                () -> config.kafkaErrorHandler(mock(KafkaTemplate.class))
        );
    }

    private KafkaProducerConfig validProducerConfig() {
        KafkaProducerConfig config = new KafkaProducerConfig();
        ReflectionTestUtils.setField(config, "deliveryTimeoutMs", 120_000);
        ReflectionTestUtils.setField(config, "requestTimeoutMs", 30_000);
        ReflectionTestUtils.setField(config, "retryBackoffMs", 500L);
        ReflectionTestUtils.setField(config, "retries", Integer.MAX_VALUE);
        ReflectionTestUtils.setField(config, "parallelRequests", 5);
        return config;
    }
}

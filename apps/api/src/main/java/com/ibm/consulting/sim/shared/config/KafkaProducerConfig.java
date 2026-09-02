package com.ibm.consulting.sim.shared.config;

import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringSerializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.kafka.KafkaProperties;
import org.springframework.boot.ssl.SslBundles;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.kafka.support.serializer.JsonSerializer;

import java.util.Map;
import java.util.HashMap;

@Configuration
public class KafkaProducerConfig {

    private static final Logger log = LoggerFactory.getLogger(KafkaProducerConfig.class);

    @Value("${app.kafka.producer.delivery-timeout-ms}")
    private int deliveryTimeoutMs;

    @Value("${app.kafka.producer.request-timeout-ms}")
    private int requestTimeoutMs;

    @Value("${app.kafka.producer.retry-backoff-ms}")
    private long retryBackoffMs;

    @Value("${app.kafka.producer.retries}")
    private int retries;

    @Value("${app.kafka.producer.parallel-requests:5}")
    private int parallelRequests;

    @Bean
    ProducerFactory<String, Object> producerFactory(
            KafkaProperties properties,
            SslBundles sslBundles) {

        // Fail during application startup rather than discovering an unsafe or
        // nonsensical reliability configuration only after traffic arrives.
        if (deliveryTimeoutMs <= 0) {
            throw new IllegalArgumentException(
                    "app.kafka.producer.delivery-timeout-ms must be positive"
            );
        }
        if (requestTimeoutMs <= 0) {
            throw new IllegalArgumentException(
                    "app.kafka.producer.request-timeout-ms must be positive"
            );
        }
        if (retryBackoffMs < 0) {
            throw new IllegalArgumentException(
                    "app.kafka.producer.retry-backoff-ms must not be negative"
            );
        }
        if (retries < 0) {
            throw new IllegalArgumentException(
                    "app.kafka.producer.retries must not be negative"
            );
        }
        if (parallelRequests < 1 || parallelRequests > 5) {
            throw new IllegalArgumentException(
                    "app.kafka.producer.parallel-requests must be between 1 and 5 "
                            + "when Kafka producer idempotence is enabled"
            );
        }

        log.info("Configuring shared Kafka JSON producer: bootstrapServerCount={}, typeHeaders=true",
                properties.getBootstrapServers().size());

        // Retain all Boot-provided SASL/SSL, client, and cloud-broker settings.
        // Rebuilding this map from only bootstrap servers silently breaks
        // authenticated production clusters.
        Map<String, Object> config = new HashMap<>(
                properties.buildProducerProperties(sslBundles)
        );

        // Broker-side producer idempotence prevents Kafka retries from appending
        // duplicate records within the lifetime of this producer session.
        config.put(
                ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG,
                true
        );

        config.put(
                ProducerConfig.ACKS_CONFIG,
                "all"
        );

        // Required by idempotent producers and preserves partition ordering
        // while still allowing multiple requests to be in flight.
        config.put(
                ProducerConfig.MAX_IN_FLIGHT_REQUESTS_PER_CONNECTION,
                parallelRequests
        );

        // Retry transient broker failures until delivery.timeout.ms expires.
        config.put(
                ProducerConfig.RETRIES_CONFIG,
                retries
        );

        // Bound the complete send lifecycle, including all retries.
        config.put(
                ProducerConfig.DELIVERY_TIMEOUT_MS_CONFIG,
                deliveryTimeoutMs
        );

        // Bound each individual broker request within the delivery window.
        config.put(
                ProducerConfig.REQUEST_TIMEOUT_MS_CONFIG,
                requestTimeoutMs
        );

        // Avoid a tight retry loop during temporary broker or network failures.
        config.put(
                ProducerConfig.RETRY_BACKOFF_MS_CONFIG,
                retryBackoffMs
        );

        config.put(
                ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG,
                StringSerializer.class
        );

        config.put(
                ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG,
                JsonSerializer.class
        );

        config.put(
                JsonSerializer.ADD_TYPE_INFO_HEADERS,
                true
        );

        return new DefaultKafkaProducerFactory<>(config);
    }

    @Bean
    KafkaTemplate<String, Object> kafkaTemplate(
            ProducerFactory<String, Object> producerFactory) {

        return new KafkaTemplate<>(producerFactory);
    }
}


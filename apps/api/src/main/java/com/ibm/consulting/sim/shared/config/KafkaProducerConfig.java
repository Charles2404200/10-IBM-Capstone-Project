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

    @Bean
    ProducerFactory<String, Object> producerFactory(
            KafkaProperties properties,
            SslBundles sslBundles) {

        log.info("Configuring shared Kafka JSON producer: bootstrapServerCount={}, typeHeaders=true",
                properties.getBootstrapServers().size());

        // Retain all Boot-provided SASL/SSL, client, and cloud-broker settings.
        // Rebuilding this map from only bootstrap servers silently breaks
        // authenticated production clusters.
        Map<String, Object> config = new HashMap<>(
                properties.buildProducerProperties(sslBundles)
        );

        // is mainly used to prevent duplicate Kafka records caused by producer retries.
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
                5
        );

        // kafka keeps retrying if failure is temporary
        config.put(
                ProducerConfig.RETRIES_CONFIG,
                retries
        );

        // we set a max time for delivery
        config.put(
                ProducerConfig.DELIVERY_TIMEOUT_MS_CONFIG,
                deliveryTimeoutMs
        );

        // we set a max time for kafka to respond to request
        config.put(
                ProducerConfig.REQUEST_TIMEOUT_MS_CONFIG,
                requestTimeoutMs
        );

        // we set a timer for how long the producer
        // needs to wait before trying because we
        // need to allow the kafka send the response
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


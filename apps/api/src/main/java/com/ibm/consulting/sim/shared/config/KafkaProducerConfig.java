package com.ibm.consulting.sim.shared.config;

import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.ByteArraySerializer;
import org.apache.kafka.common.serialization.Serializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.kafka.KafkaProperties;
import org.springframework.boot.ssl.SslBundles;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.kafka.support.serializer.DelegatingByTypeSerializer;
import org.springframework.kafka.support.serializer.JsonSerializer;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

@Configuration
public class KafkaProducerConfig {

    private static final Logger log = LoggerFactory.getLogger(KafkaProducerConfig.class);

    private final KafkaProducerProperties properties;

    public KafkaProducerConfig(KafkaProducerProperties properties) {
        this.properties = properties;
    }

    @Bean
    ProducerFactory<String, Object> producerFactory(
            KafkaProperties kafkaProperties,
            SslBundles sslBundles) {

        log.info("Configuring shared Kafka JSON producer: bootstrapServerCount={}, typeHeaders=true",
                kafkaProperties.getBootstrapServers().size());

        // Retain all Boot-provided SASL/SSL, client, and cloud-broker settings.
        // Rebuilding this map from only bootstrap servers silently breaks
        // authenticated production clusters.
        Map<String, Object> config = new HashMap<>(
                kafkaProperties.buildProducerProperties(sslBundles)
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
                this.properties.parallelRequests()
        );

        // Retry transient broker failures until delivery.timeout.ms expires.
        config.put(
                ProducerConfig.RETRIES_CONFIG,
                this.properties.retries()
        );

        // Bound the complete send lifecycle, including all retries.
        config.put(
                ProducerConfig.DELIVERY_TIMEOUT_MS_CONFIG,
                kafkaMilliseconds(this.properties.deliveryTimeout(), "deliveryTimeout")
        );

        // Bound each individual broker request within the delivery window.
        config.put(
                ProducerConfig.REQUEST_TIMEOUT_MS_CONFIG,
                kafkaMilliseconds(this.properties.requestTimeout(), "requestTimeout")
        );

        // Avoid a tight retry loop during temporary broker or network failures.
        config.put(
                ProducerConfig.RETRY_BACKOFF_MS_CONFIG,
                this.properties.retryBackoff().toMillis()
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

        // Deserialization failures are recovered with their original byte[]. A
        // type-delegating serializer preserves those poison bytes on the DLT,
        // while normal application events continue to use JSON serialization.
        Map<Class<?>, Serializer<?>> valueSerializers = new LinkedHashMap<>();
        valueSerializers.put(byte[].class, new ByteArraySerializer());
        valueSerializers.put(Object.class, new JsonSerializer<>());

        return new DefaultKafkaProducerFactory<>(
                config,
                new StringSerializer(),
                new DelegatingByTypeSerializer(valueSerializers, true)
        );
    }

    @Bean
    KafkaTemplate<String, Object> kafkaTemplate(
            ProducerFactory<String, Object> producerFactory) {

        return new KafkaTemplate<>(producerFactory);
    }

    private int kafkaMilliseconds(java.time.Duration value, String propertyName) {
        try {
            return Math.toIntExact(value.toMillis());
        } catch (ArithmeticException overflow) {
            throw new IllegalArgumentException(
                    "Kafka producer " + propertyName + " exceeds the supported millisecond range",
                    overflow
            );
        }
    }
}


package com.ibm.consulting.sim.shared.config;

import com.ibm.consulting.sim.shared.domain.outbox.EventEnvelope;
import com.ibm.consulting.sim.shared.domain.kafka.InvalidKafkaEventException;
import com.ibm.consulting.sim.shared.domain.kafka.InvalidKafkaPayloadException;
import com.ibm.consulting.sim.shared.domain.kafka.UnsupportedKafkaEventException;

import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.serialization.StringDeserializer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.kafka.KafkaProperties;
import org.springframework.boot.ssl.SslBundles;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.support.serializer.ErrorHandlingDeserializer;
import org.springframework.kafka.support.serializer.JsonDeserializer;
import org.springframework.util.backoff.FixedBackOff;

import java.util.HashMap;
import java.util.Map;

@Configuration
@EnableKafka
public class KafkaConsumerConfig {

    private static final Logger log =
            LoggerFactory.getLogger(KafkaConsumerConfig.class);

    @Value("${app.kafka.consumer.retry-backoff-ms:2000}")
    private long retryBackoffMs;

    @Value("${app.kafka.consumer.max-retries:3}")
    private long maxRetries;


    @Bean
    public DefaultErrorHandler kafkaErrorHandler(
            KafkaTemplate<String, Object> kafkaTemplate
    ) {

        DeadLetterPublishingRecoverer recoverer =
                new DeadLetterPublishingRecoverer(
                        kafkaTemplate,

                        // If processing permanently fails,
                        // send the record to <original-topic>.DLT
                        (record, exception) ->
                                new TopicPartition(
                                        record.topic() + ".DLT",
                                        record.partition()
                                )
                );

        FixedBackOff backOff =
                new FixedBackOff(
                        retryBackoffMs,
                        maxRetries
                );

        DefaultErrorHandler errorHandler =
                new DefaultErrorHandler(
                        recoverer,
                        backOff
                );

        /*
         * These represent invalid/unsupported messages.
         * Retrying them will not fix them, so send them
         * directly to the DLT.
         */
        errorHandler.addNotRetryableExceptions(
                InvalidKafkaEventException.class,
                InvalidKafkaPayloadException.class,
                UnsupportedKafkaEventException.class
        );

        return errorHandler;
    }


    @Bean
    public ConsumerFactory<String, Object> consumerFactory(
            KafkaProperties properties,
            SslBundles sslBundles
    ) {

        /*
         * Start with Spring Boot's Kafka configuration.
         *
         * This preserves:
         *   spring.kafka.bootstrap-servers
         *   consumer properties
         *   security settings
         *   SSL/SASL settings
         *   etc.
         */
        Map<String, Object> config =
                new HashMap<>(
                        properties.buildConsumerProperties(sslBundles)
                );

        log.info(
                "Configuring Kafka consumer: bootstrapServers={}",
                properties.getBootstrapServers()
        );


        /*
         * ErrorHandlingDeserializer wraps the real
         * deserializers.
         */
        config.put(
                ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG,
                ErrorHandlingDeserializer.class
        );

        config.put(
                ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG,
                ErrorHandlingDeserializer.class
        );


        /*
         * Actual key deserializer.
         */
        config.put(
                ErrorHandlingDeserializer.KEY_DESERIALIZER_CLASS,
                StringDeserializer.class
        );


        /*
         * Actual value deserializer.
         */
        config.put(
                ErrorHandlingDeserializer.VALUE_DESERIALIZER_CLASS,
                JsonDeserializer.class
        );


        /*
         * Only allow classes from our application.
         */
        config.put(
                JsonDeserializer.TRUSTED_PACKAGES,
                "com.ibm.consulting.sim.*"
        );


        /*
         * We know every Kafka event contains EventEnvelope.
         *
         * Therefore we don't need Spring's __TypeId__
         * Kafka header to determine the Java class.
         */
        config.put(
                JsonDeserializer.USE_TYPE_INFO_HEADERS,
                false
        );


        /*
         * Without type headers, tell JsonDeserializer
         * exactly which Java class the JSON represents.
         */
        config.put(
                JsonDeserializer.VALUE_DEFAULT_TYPE,
                EventEnvelope.class.getName()
        );


        return new DefaultKafkaConsumerFactory<>(
                config
        );
    }


    @Bean("kafkaListenerContainerFactory")
    public ConcurrentKafkaListenerContainerFactory<String, Object>
    kafkaListenerContainerFactory(
            ConsumerFactory<String, Object> consumerFactory,
            DefaultErrorHandler kafkaErrorHandler
    ) {

        var factory =
                new ConcurrentKafkaListenerContainerFactory<String, Object>();

        factory.setConsumerFactory(
                consumerFactory
        );

        factory.setCommonErrorHandler(
                kafkaErrorHandler
        );

        return factory;
    }
}

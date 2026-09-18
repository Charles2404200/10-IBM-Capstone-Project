package com.ibm.consulting.sim.shared.config;

import com.ibm.consulting.sim.shared.domain.outbox.EventEnvelope;
import com.ibm.consulting.sim.shared.domain.kafka.InvalidKafkaEventException;
import com.ibm.consulting.sim.shared.domain.kafka.InvalidKafkaPayloadException;
import com.ibm.consulting.sim.shared.domain.kafka.UnsupportedKafkaEventException;
import com.ibm.consulting.sim.shared.application.kafka.KafkaDltMetrics;
import com.ibm.consulting.sim.admin.infrastructure.NotificationKafkaProperties;

import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.ByteArrayDeserializer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.beans.factory.annotation.Qualifier;
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
import org.springframework.kafka.listener.ContainerProperties;
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

    private final KafkaConsumerReliabilityProperties consumerProperties;
    private final NotificationKafkaProperties notificationProperties;

    public KafkaConsumerConfig(
            KafkaConsumerReliabilityProperties consumerProperties,
            NotificationKafkaProperties notificationProperties) {
        this.consumerProperties = consumerProperties;
        this.notificationProperties = notificationProperties;
    }

    @Bean
    public DeadLetterPublishingRecoverer deadLetterPublishingRecoverer(
            KafkaTemplate<String, Object> kafkaTemplate
    ) {
        return new DeadLetterPublishingRecoverer(
                kafkaTemplate,
                (record, exception) -> new TopicPartition(
                        notificationProperties.dlt().topicName(),
                        record.partition()
                )
        );
    }

    @Bean
    public DefaultErrorHandler kafkaErrorHandler(
            DeadLetterPublishingRecoverer recoverer
    ) {
        FixedBackOff backOff =
                new FixedBackOff(
                        consumerProperties.retryBackoff().toMillis(),
                        consumerProperties.maxRetries()
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

    /**
     * DLT failures are logged once and acknowledged; they are never passed to a
     * DeadLetterPublishingRecoverer, preventing recursive .DLT.DLT topics.
     */
    @Bean
    public DefaultErrorHandler kafkaDltErrorHandler(KafkaDltMetrics metrics) {
        return new DefaultErrorHandler(
                (record, failure) -> {
                    metrics.recordHandlingFailure();
                    log.error(
                            "Kafka DLT handler failed: topic={}, partition={}, offset={}",
                            record.topic(),
                            record.partition(),
                            record.offset(),
                            failure
                    );
                },
                new FixedBackOff(0L, 0L)
        );
    }


    @Bean
    public ConsumerFactory<String, EventEnvelope> eventEnvelopeConsumerFactory(
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


    @Bean("eventEnvelopeKafkaListenerContainerFactory")
    public ConcurrentKafkaListenerContainerFactory<String, EventEnvelope>
    eventEnvelopeKafkaListenerContainerFactory(
            ConsumerFactory<String, EventEnvelope> eventEnvelopeConsumerFactory,
            @Qualifier("kafkaErrorHandler") DefaultErrorHandler kafkaErrorHandler
    ) {

        var factory =
                new ConcurrentKafkaListenerContainerFactory<String, EventEnvelope>();

        factory.setConsumerFactory(
                eventEnvelopeConsumerFactory
        );

        factory.setCommonErrorHandler(
                kafkaErrorHandler
        );

        // Commit each successfully handled record. If the process stops after
        // the database commit but before this acknowledgement, inbox
        // idempotency makes the replay harmless.
        factory.getContainerProperties().setAckMode(
                ContainerProperties.AckMode.RECORD
        );

        return factory;
    }

    /**
     * DLT records are consumed as opaque bytes. This lets operations observe
     * records that failed JSON deserialization without feeding them through
     * the primary error handler and producing recursive .DLT.DLT topics.
     */
    @Bean("kafkaDltListenerContainerFactory")
    public ConcurrentKafkaListenerContainerFactory<String, byte[]>
    kafkaDltListenerContainerFactory(
            KafkaProperties properties,
            SslBundles sslBundles,
            @Qualifier("kafkaDltErrorHandler") DefaultErrorHandler kafkaDltErrorHandler
    ) {
        Map<String, Object> config = new HashMap<>(
                properties.buildConsumerProperties(sslBundles)
        );
        config.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        config.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, ByteArrayDeserializer.class);

        var factory = new ConcurrentKafkaListenerContainerFactory<String, byte[]>();
        factory.setConsumerFactory(new DefaultKafkaConsumerFactory<>(config));
        factory.setCommonErrorHandler(kafkaDltErrorHandler);
        factory.getContainerProperties().setAckMode(ContainerProperties.AckMode.RECORD);
        return factory;
    }
}

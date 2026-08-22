package com.ibm.consulting.sim.shared.config;

import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.kafka.KafkaProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.beans.factory.annotation.Value;
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

    private static final Logger log = LoggerFactory.getLogger(KafkaConsumerConfig.class);

    @Value("${app.kafka.consumer.retry-backoff-ms}")
    private long retryBackoffMs;

    @Value("${app.kafka.consumer.max-retries}")
    private long maxRetries;

    @Bean
    public DefaultErrorHandler kafkaErrorHandler(
            KafkaTemplate<String, Object> kafkaTemplate
    ) {

        DeadLetterPublishingRecoverer recoverer =
                new DeadLetterPublishingRecoverer(
                        kafkaTemplate,
                        // we create dead letter topic
                        // if the consumer fails to
                        // to consume the message from
                        // kafka so that we can latter discover
                        // what is wrong with the message
                        // by retrieving the data from that topic
                        (record, exception) ->
                                new TopicPartition(
                                        record.topic() + ".DLT",
                                        record.partition()
                                )
                );

        DefaultErrorHandler errorHandler = getDefaultErrorHandler(recoverer);

        // when kafka listioner throws an error
        // it is received by the errorHandler
        // and then if it checks it IllegalArgumentException
        // no point retrying so addNotRetryingExceptions
        // so it talls something is wrong in code
        errorHandler.addNotRetryableExceptions(
                IllegalArgumentException.class
        );

        return errorHandler;
    }

    private DefaultErrorHandler getDefaultErrorHandler(DeadLetterPublishingRecoverer recoverer) {
        FixedBackOff backOff =
                new FixedBackOff(
                        retryBackoffMs, // retry wait time
                        maxRetries    // 3 retries
                );

        DefaultErrorHandler errorHandler =
                new DefaultErrorHandler(
                        // for message recovery
                        recoverer,
                        // try to pull or consume
                        // for max retries if not
                        // successful then Dead DeadLetterPublishingRecoverer
                        // pushes it Dead Letter topic
                        backOff
                );
        return errorHandler;
    }

    @Bean
    ConsumerFactory<String, Object> consumerFactory(KafkaProperties properties) {
        log.info("Configuring shared Kafka JSON consumer: bootstrapServerCount={}, typeHeaders=true",
                properties.getBootstrapServers().size());
        JsonDeserializer<Object> deserializer = new JsonDeserializer<>();
        deserializer.addTrustedPackages("com.ibm.consulting.sim.*");
        deserializer.setUseTypeHeaders(true);

        Map<String, Object> config = new HashMap<>();
        config.put(
                ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG,
                properties.getBootstrapServers()
        );

        config.put(
                ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG,
                ErrorHandlingDeserializer.class
        );

        config.put(
                ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG,
                ErrorHandlingDeserializer.class
        );

        config.put(
                ErrorHandlingDeserializer.KEY_DESERIALIZER_CLASS,
                StringDeserializer.class
        );

        config.put(
                ErrorHandlingDeserializer.VALUE_DESERIALIZER_CLASS,
                JsonDeserializer.class
        );

        return new DefaultKafkaConsumerFactory<>(
                config
        );
    }

    @Bean("kafkaListenerContainerFactory")
    ConcurrentKafkaListenerContainerFactory<String, Object> kafkaListenerContainerFactory(
            ConsumerFactory<String, Object> consumerFactory,
            DefaultErrorHandler kafkaErrorHandler) {

        var factory = new ConcurrentKafkaListenerContainerFactory<String, Object>();
        factory.setConsumerFactory(consumerFactory);
        factory.setCommonErrorHandler(
                kafkaErrorHandler
        );
        return factory;
    }
}

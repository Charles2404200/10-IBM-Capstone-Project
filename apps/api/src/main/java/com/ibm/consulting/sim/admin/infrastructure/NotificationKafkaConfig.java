package com.ibm.consulting.sim.admin.infrastructure;

import org.apache.kafka.clients.admin.NewTopic;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.config.TopicBuilder;

@Configuration
public class NotificationKafkaConfig {

    private static final Logger log = LoggerFactory.getLogger(NotificationKafkaConfig.class);

    private final NotificationKafkaProperties properties;
    private final String dltTopicName;

    public NotificationKafkaConfig(
            NotificationKafkaProperties properties,
            @Value("${app.kafka.notifications.dlt.topic-name:notifications.DLT}") String dltTopicName) {
        this.properties = properties;
        this.dltTopicName = dltTopicName;
    }

    @Bean
    public NewTopic notificationTopic() {
        log.info("Configuring notification Kafka topic: name={}, partitions={}, replicas={}",
                properties.topic().name(), properties.topic().partitions(), properties.topic().replicas());
        return TopicBuilder
                .name(properties.topic().name())
                .partitions(properties.topic().partitions())
                .replicas(properties.topic().replicas())
                .build();
    }

    @Bean
    public NewTopic notificationDeadLetterTopic() {
        return TopicBuilder
                .name(dltTopicName)
                .partitions(properties.topic().partitions())
                .replicas(properties.topic().replicas())
                .build();
    }
}

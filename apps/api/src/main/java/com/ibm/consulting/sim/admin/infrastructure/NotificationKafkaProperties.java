package com.ibm.consulting.sim.admin.infrastructure;

import com.ibm.consulting.sim.shared.config.KafkaConsumerProperties;
import com.ibm.consulting.sim.shared.config.KafkaTopicProperties;
import jakarta.validation.Valid;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "app.kafka.notifications")
public record NotificationKafkaProperties(
        @Valid KafkaTopicProperties topic,
        @Valid KafkaConsumerProperties consumer
) {}

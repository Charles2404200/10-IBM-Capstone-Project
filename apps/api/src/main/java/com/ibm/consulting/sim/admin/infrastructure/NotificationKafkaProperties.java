package com.ibm.consulting.sim.admin.infrastructure;

import com.ibm.consulting.sim.shared.config.KafkaConsumerProperties;
import com.ibm.consulting.sim.shared.config.KafkaDltProperties;
import com.ibm.consulting.sim.shared.config.KafkaTopicProperties;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "app.kafka.notifications")
public record NotificationKafkaProperties(
        @NotNull @Valid KafkaTopicProperties topic,
        @NotNull @Valid KafkaConsumerProperties consumer,
        @NotNull @Valid KafkaDltProperties dlt
) {}

package com.ibm.consulting.sim.shared.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record KafkaDltProperties(
        @NotBlank String topicName,
        @NotNull @Valid KafkaConsumerProperties consumer
) {
}

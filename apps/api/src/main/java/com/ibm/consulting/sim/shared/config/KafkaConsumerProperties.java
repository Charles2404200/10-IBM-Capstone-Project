package com.ibm.consulting.sim.shared.config;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

public record KafkaConsumerProperties(
        @NotBlank String groupId,
        @Min(1) int concurrency
) {}

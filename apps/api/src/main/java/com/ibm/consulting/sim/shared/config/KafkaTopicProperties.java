package com.ibm.consulting.sim.shared.config;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

public record KafkaTopicProperties(
        @NotBlank String name,
        @Min(1) int partitions,
        @Min(1) int replicas
) {}

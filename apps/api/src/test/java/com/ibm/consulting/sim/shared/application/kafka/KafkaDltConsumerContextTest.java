package com.ibm.consulting.sim.shared.application.kafka;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.FilterType;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.mock;

@SpringJUnitConfig(KafkaDltConsumerContextTest.ComponentDiscoveryConfiguration.class)
class KafkaDltConsumerContextTest {

    @Autowired
    private KafkaDltConsumer consumer;

    @Test
    void kafkaDltConsumerIsDiscoveredByComponentScanning() {
        assertNotNull(consumer);
    }

    @Configuration(proxyBeanMethods = false)
    @ComponentScan(
            basePackageClasses = KafkaDltConsumer.class,
            useDefaultFilters = false,
            includeFilters = @ComponentScan.Filter(
                    type = FilterType.ASSIGNABLE_TYPE,
                    classes = KafkaDltConsumer.class
            )
    )
    static class ComponentDiscoveryConfiguration {
        @org.springframework.context.annotation.Bean
        KafkaDltMetrics kafkaDltMetrics() {
            return mock(KafkaDltMetrics.class);
        }
    }
}

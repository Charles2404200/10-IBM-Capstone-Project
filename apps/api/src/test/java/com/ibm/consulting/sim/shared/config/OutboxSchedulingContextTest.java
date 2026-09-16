package com.ibm.consulting.sim.shared.config;

import com.ibm.consulting.sim.shared.application.kafka.KafkaEventPublisher;
import com.ibm.consulting.sim.shared.application.outbox.OutboxClaimService;
import com.ibm.consulting.sim.shared.application.outbox.OutboxDispatcher;
import com.ibm.consulting.sim.shared.application.outbox.OutboxMetrics;
import com.ibm.consulting.sim.shared.application.outbox.OutboxRecoveryScheduler;
import com.ibm.consulting.sim.shared.application.outbox.OutboxStateService;
import com.ibm.consulting.sim.shared.domain.outbox.OutboxEventRepository;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class OutboxSchedulingContextTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(SchedulingConfiguration.class)
            .withPropertyValues(
                    "app.kafka.outbox.poll-delay=200ms",
                    "app.kafka.outbox.recovery-delay=30s"
            );

    @Test
    void durationBasedOutboxSchedulesAreAcceptedAtStartup() {
        contextRunner.run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).hasSingleBean(OutboxDispatcher.class);
            assertThat(context).hasSingleBean(OutboxRecoveryScheduler.class);
        });
    }

    @Configuration(proxyBeanMethods = false)
    @EnableScheduling
    @EnableConfigurationProperties({OutboxProperties.class, KafkaProducerProperties.class})
    @Import({
            OutboxDispatcher.class,
            OutboxRecoveryScheduler.class,
            OutboxScheduleIntervals.class
    })
    static class SchedulingConfiguration {

        @Bean
        OutboxClaimService outboxClaimService() {
            OutboxClaimService service = mock(OutboxClaimService.class);
            when(service.claimBatch(anyInt(), any())).thenReturn(List.of());
            return service;
        }

        @Bean
        OutboxEventRepository outboxEventRepository() {
            return mock(OutboxEventRepository.class);
        }

        @Bean
        OutboxStateService outboxStateService() {
            return mock(OutboxStateService.class);
        }

        @Bean
        KafkaEventPublisher kafkaEventPublisher() {
            return mock(KafkaEventPublisher.class);
        }

        @Bean
        OutboxMetrics outboxMetrics() {
            return mock(OutboxMetrics.class);
        }

        @Bean(name = "outboxCompletionExecutor", destroyMethod = "shutdownNow")
        ExecutorService outboxCompletionExecutor() {
            return Executors.newSingleThreadExecutor();
        }
    }
}

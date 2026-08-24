package com.ibm.consulting.sim.shared.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.AsyncConfigurer;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;

/**
 * Bounded, named, application-managed thread pools — replacing ad-hoc
 * {@code Executors.newCachedThreadPool()} calls (unbounded, unnamed, never
 * shut down explicitly) with Spring-lifecycle-managed executors that are
 * sized, observable, and cleanly stopped on context shutdown.
 *
 * <p>{@link #aiGatewayExecutor()} bounds concurrent outbound AI calls so a
 * burst of learner requests can't exhaust the JVM's thread budget; excess
 * work queues instead of spawning unbounded threads.
 */
@Configuration
@EnableAsync
public class AsyncConfig implements AsyncConfigurer {

    private final int corePoolSize;
    private final int maxPoolSize;
    private final int queueCapacity;

    public AsyncConfig(@Value("${app.async.core-pool-size:8}") int corePoolSize,
                        @Value("${app.async.max-pool-size:32}") int maxPoolSize,
                        @Value("${app.async.queue-capacity:200}") int queueCapacity) {
        this.corePoolSize = corePoolSize;
        this.maxPoolSize = maxPoolSize;
        this.queueCapacity = queueCapacity;
    }

    /** Bounded pool dedicated to outbound AI gateway calls (watsonx.ai). */
    @Bean(destroyMethod = "shutdown")
    public ExecutorService aiGatewayExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(corePoolSize);
        executor.setMaxPoolSize(maxPoolSize);
        executor.setQueueCapacity(queueCapacity);
        executor.setThreadNamePrefix("ai-gateway-");
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(10);
        executor.initialize();
        return executor.getThreadPoolExecutor();
    }

    /**
     * Isolated pool for provider fan-out. Meeting/STOMP workers must never wait
     * behind slow model HTTP calls, especially when one learner turn races more
     * than one provider.
     */
    @Bean(destroyMethod = "shutdown")
    public ExecutorService aiProviderExecutor(
            @Value("${app.async.ai-provider-core-pool-size:12}") int providerCorePoolSize,
            @Value("${app.async.ai-provider-max-pool-size:48}") int providerMaxPoolSize,
            @Value("${app.async.ai-provider-queue-capacity:300}") int providerQueueCapacity) {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(providerCorePoolSize);
        executor.setMaxPoolSize(providerMaxPoolSize);
        executor.setQueueCapacity(providerQueueCapacity);
        executor.setThreadNamePrefix("ai-provider-");
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(10);
        executor.initialize();
        return executor.getThreadPoolExecutor();
    }

    /**
     * Non-critical client wording must never delay a persisted proposal outcome.
     * The decision engine completes synchronously; this pool enriches its natural
     * language response after commit.
     */
    @Bean(destroyMethod = "shutdown")
    public ExecutorService proposalNarrativeExecutor(
            @Value("${app.async.proposal-narrative-core-pool-size:2}") int corePoolSize,
            @Value("${app.async.proposal-narrative-max-pool-size:8}") int maxPoolSize,
            @Value("${app.async.proposal-narrative-queue-capacity:100}") int queueCapacity) {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(corePoolSize);
        executor.setMaxPoolSize(maxPoolSize);
        executor.setQueueCapacity(queueCapacity);
        executor.setThreadNamePrefix("proposal-narrative-");
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(10);
        executor.initialize();
        return executor.getThreadPoolExecutor();
    }

    /** Keeps assessment coaching off the assessment/portfolio completion path. */
    @Bean(destroyMethod = "shutdown")
    public ExecutorService assessmentFeedbackExecutor(
            @Value("${app.async.assessment-feedback-core-pool-size:2}") int corePoolSize,
            @Value("${app.async.assessment-feedback-max-pool-size:8}") int maxPoolSize,
            @Value("${app.async.assessment-feedback-queue-capacity:100}") int queueCapacity) {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(corePoolSize);
        executor.setMaxPoolSize(maxPoolSize);
        executor.setQueueCapacity(queueCapacity);
        executor.setThreadNamePrefix("assessment-feedback-");
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(10);
        executor.initialize();
        return executor.getThreadPoolExecutor();
    }

    /** General-purpose pool for {@code @Async}-annotated application methods. */
    @Override
    public Executor getAsyncExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(corePoolSize);
        executor.setMaxPoolSize(maxPoolSize);
        executor.setQueueCapacity(queueCapacity);
        executor.setThreadNamePrefix("app-async-");
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(10);
        executor.initialize();
        return executor;
    }
}

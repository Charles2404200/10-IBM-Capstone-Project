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

package com.anbit.archetype.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.AsyncTaskExecutor;
import org.springframework.core.task.SimpleAsyncTaskExecutor;
import org.springframework.scheduling.annotation.EnableAsync;

/**
 * Enables {@code @Async} and provides a dedicated virtual-thread executor for background
 * work. On Java 25 virtual threads make blocking background tasks cheap, so a per-task
 * thread is fine — no pool tuning needed.
 */
@Configuration
@EnableAsync
public class AsyncConfig {

    @Bean
    public AsyncTaskExecutor fulfillmentExecutor() {
        SimpleAsyncTaskExecutor executor = new SimpleAsyncTaskExecutor("fulfillment-");
        executor.setVirtualThreads(true);
        return executor;
    }
}

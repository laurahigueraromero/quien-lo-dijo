package com.quienlodijo.backend.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

/**
 * Scheduler para los temporizadores de fase de cada ronda (responder/apostar,
 * plan.md §3, T016). Un pool pequeño basta: como mucho hay un timer activo
 * por sala en este MVP de instancia única.
 */
@Configuration
public class SchedulingConfig {

    @Bean
    public TaskScheduler taskScheduler() {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(4);
        scheduler.setThreadNamePrefix("round-timer-");
        scheduler.initialize();
        return scheduler;
    }
}

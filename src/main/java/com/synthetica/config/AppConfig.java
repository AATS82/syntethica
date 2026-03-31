package com.synthetica.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.AsyncConfigurer;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.concurrent.Executor;

@Configuration
public class AppConfig implements WebMvcConfigurer, AsyncConfigurer {

    @Value("${app.async.core-pool-size:10}")
    private int corePoolSize;

    @Value("${app.async.max-pool-size:20}")
    private int maxPoolSize;

    @Value("${app.async.queue-capacity:50}")
    private int queueCapacity;

    // ── CORS ──────────────────────────────────────────────────────────────────
    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**")
                .allowedOrigins(
                    "http://localhost:4200",   // Angular dev
                    "https://*.vercel.app"     // Vercel deploy
                )
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
                .allowedHeaders("Authorization", "Content-Type", "X-Requested-With")
                .allowCredentials(false);
    }

    // ── ASYNC THREAD POOL ─────────────────────────────────────────────────────
    // Usado por SimulacionService para llamadas paralelas a Claude
    @Override
    @Bean(name = "simulacionExecutor")
    public Executor getAsyncExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(corePoolSize);
        executor.setMaxPoolSize(maxPoolSize);
        executor.setQueueCapacity(queueCapacity);
        executor.setThreadNamePrefix("simulacion-");
        executor.initialize();
        return executor;
    }
}

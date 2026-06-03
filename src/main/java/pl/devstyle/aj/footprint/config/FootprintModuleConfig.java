package pl.devstyle.aj.footprint.config;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.retry.annotation.EnableRetry;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

@Configuration
@EnableRetry
@EnableAsync
class FootprintModuleConfig {

    /**
     * Provides a {@link MeterRegistry} when Spring Boot Actuator is not on the classpath.
     * If a real registry (Prometheus/JMX/etc.) is wired in production, this fallback steps aside.
     */
    @Bean
    @ConditionalOnMissingBean(MeterRegistry.class)
    MeterRegistry footprintMeterRegistry() {
        return new SimpleMeterRegistry();
    }

    @Bean(name = "footprintAuditExecutor")
    Executor footprintAuditExecutor(
            @Value("${app.footprint.audit.async.core-pool-size:2}") int corePoolSize,
            @Value("${app.footprint.audit.async.max-pool-size:8}") int maxPoolSize,
            @Value("${app.footprint.audit.async.queue-capacity:100}") int queueCapacity) {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(corePoolSize);
        executor.setMaxPoolSize(maxPoolSize);
        executor.setQueueCapacity(queueCapacity);
        executor.setThreadNamePrefix("footprint-audit-");
        executor.initialize();
        return executor;
    }
}

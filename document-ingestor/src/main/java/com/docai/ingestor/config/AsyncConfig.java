package com.docai.ingestor.config;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Executor;

import org.slf4j.MDC;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.TaskDecorator;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;

@Configuration
public class AsyncConfig {

    @Bean(name = "taskExecutor")
    public Executor taskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(4);
        executor.setMaxPoolSize(8);
        executor.setQueueCapacity(100);
        executor.setThreadNamePrefix("ingest-");
        executor.setTaskDecorator(new ContextPropagatingTaskDecorator());
        executor.initialize();
        return executor;
    }

    /** Snapshots {@link TenantContext}, the Spring Security context, and the MDC (tenantId/userId
     * — see JwtTokenFilter) on the submitting (request) thread and restores them on the pool
     * thread — none of these ThreadLocals cross an executor boundary on their own, so without
     * this every {@code @Async} ingestion (see IngestionService#ingestUploadedFile) would lose
     * tenant scoping and log correlation the moment it hands off to the pool. Mirrors
     * documentation-bot's own AsyncConfig. Always clears everything in a finally block so a
     * reused pool thread never leaks state into the next task. */
    private static final class ContextPropagatingTaskDecorator implements TaskDecorator {
        @Override
        public Runnable decorate(Runnable runnable) {
            UUID tenantId = TenantContext.getOrNull();
            SecurityContext securityContext = SecurityContextHolder.getContext();
            Map<String, String> mdcContext = MDC.getCopyOfContextMap();
            return () -> {
                try {
                    if (tenantId != null) TenantContext.set(tenantId);
                    SecurityContextHolder.setContext(securityContext);
                    if (mdcContext != null) MDC.setContextMap(mdcContext);
                    runnable.run();
                } finally {
                    TenantContext.clear();
                    SecurityContextHolder.clearContext();
                    MDC.clear();
                }
            };
        }
    }
}

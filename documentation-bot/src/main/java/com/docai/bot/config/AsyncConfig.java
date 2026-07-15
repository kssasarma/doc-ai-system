package com.docai.bot.config;

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
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(4);
        executor.setQueueCapacity(50);
        executor.setThreadNamePrefix("bot-async-");
        executor.setTaskDecorator(new ContextPropagatingTaskDecorator());
        executor.initialize();
        return executor;
    }

    /** Snapshots {@link TenantContext}, the Spring Security context, and the MDC (tenantId/userId
     * — see TenantResolutionFilter) on the submitting (request) thread and restores them on the
     * pool thread — without this, any {@code @Async} method that calls something depending on
     * any of these (e.g. {@code LLMRouter} → tenant LLM config, an audit write reading the
     * current principal, or just a log line that should carry the request's tenantId/userId)
     * fails or silently loses that context, since ThreadLocals never cross an executor boundary
     * on their own. Always clears everything in a finally block to avoid leaking state onto a
     * reused pool thread. */
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

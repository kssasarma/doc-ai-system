package com.docai.bot.config;

import java.time.Duration;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import io.github.resilience4j.bulkhead.Bulkhead;
import io.github.resilience4j.bulkhead.BulkheadConfig;
import io.github.resilience4j.bulkhead.BulkheadRegistry;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;

/**
 * Phase 0.3 — Resilience4j circuit breaker and bulkhead for all LLM calls.
 *
 * Circuit breaker opens after 50 % failure rate over a 10-call sliding window
 * and stays open for 30 s before allowing a probe call.
 *
 * Bulkhead limits concurrent LLM calls to 5, queuing up to 5 s before
 * rejecting with BulkheadFullException.
 */
@Configuration
public class ResilienceConfig {

    public static final String LLM_INSTANCE = "llm";

    @Bean
    public CircuitBreakerRegistry circuitBreakerRegistry() {
        CircuitBreakerConfig config = CircuitBreakerConfig.custom()
            .slidingWindowType(CircuitBreakerConfig.SlidingWindowType.COUNT_BASED)
            .slidingWindowSize(10)
            .minimumNumberOfCalls(5)
            .failureRateThreshold(50.0f)
            .waitDurationInOpenState(Duration.ofSeconds(30))
            .permittedNumberOfCallsInHalfOpenState(3)
            .automaticTransitionFromOpenToHalfOpenEnabled(true)
            .recordExceptions(Exception.class)
            .build();
        return CircuitBreakerRegistry.of(config);
    }

    @Bean
    public BulkheadRegistry bulkheadRegistry() {
        BulkheadConfig config = BulkheadConfig.custom()
            .maxConcurrentCalls(5)
            .maxWaitDuration(Duration.ofSeconds(5))
            .build();
        return BulkheadRegistry.of(config);
    }

    @Bean("llmCircuitBreaker")
    public CircuitBreaker llmCircuitBreaker(CircuitBreakerRegistry registry) {
        return registry.circuitBreaker(LLM_INSTANCE);
    }

    @Bean("llmBulkhead")
    public Bulkhead llmBulkhead(BulkheadRegistry registry) {
        return registry.bulkhead(LLM_INSTANCE);
    }
}

package com.docai.ingestor.config;

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
 * Phase 0.3 — Resilience4j circuit breaker and bulkhead for embedding API calls.
 *
 * Circuit breaker opens after 50 % failure rate over a 10-call sliding window
 * and stays open for 30 s before allowing a probe call.
 *
 * Bulkhead limits concurrent embedding API calls to 5 to prevent flooding
 * the upstream API under burst ingestion load.
 */
@Configuration
public class ResilienceConfig {

    public static final String EMBEDDING_INSTANCE = "embedding";

    @Bean
    CircuitBreakerRegistry circuitBreakerRegistry() {
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
    BulkheadRegistry bulkheadRegistry() {
        BulkheadConfig config = BulkheadConfig.custom()
            .maxConcurrentCalls(5)
            .maxWaitDuration(Duration.ofSeconds(10))
            .build();
        return BulkheadRegistry.of(config);
    }

    @Bean("embeddingCircuitBreaker")
    CircuitBreaker embeddingCircuitBreaker(CircuitBreakerRegistry registry) {
        return registry.circuitBreaker(EMBEDDING_INSTANCE);
    }

    @Bean("embeddingBulkhead")
    Bulkhead embeddingBulkhead(BulkheadRegistry registry) {
        return registry.bulkhead(EMBEDDING_INSTANCE);
    }
}

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
 *
 * The rerank pass (ReRankingService) gets its own, separate instance ({@link #RERANK_INSTANCE})
 * rather than sharing {@link #LLM_INSTANCE} with answer generation. Re-ranking is explicitly
 * best-effort — a failure just falls back to MMR order — but it typically runs against a
 * different, tenant-configurable model (TenantLLMConfig.rerankModel) than the primary chat model.
 * If that model is misconfigured (e.g. pointed at a model/provider combination the LLM gateway
 * can't route), every rerank call fails; sharing one breaker meant those failures counted against
 * the same failure budget as answer generation and could trip it open, declining legitimate
 * chat/streaming requests even though the primary chat model was perfectly healthy.
 */
@Configuration
public class ResilienceConfig {

    public static final String LLM_INSTANCE = "llm";
    public static final String RERANK_INSTANCE = "llm-rerank";

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

    @Bean("llmRerankCircuitBreaker")
    public CircuitBreaker llmRerankCircuitBreaker(CircuitBreakerRegistry registry) {
        return registry.circuitBreaker(RERANK_INSTANCE);
    }

    @Bean("llmRerankBulkhead")
    public Bulkhead llmRerankBulkhead(BulkheadRegistry registry) {
        return registry.bulkhead(RERANK_INSTANCE);
    }
}

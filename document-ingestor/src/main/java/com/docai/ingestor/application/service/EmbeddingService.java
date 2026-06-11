package com.docai.ingestor.application.service;

import java.util.List;

import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.embedding.EmbeddingResponse;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import io.github.resilience4j.bulkhead.Bulkhead;
import io.github.resilience4j.bulkhead.BulkheadFullException;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class EmbeddingService {

    private static final int MAX_ATTEMPTS = 3;
    private static final long INITIAL_BACKOFF_MS = 1000L;

    private final EmbeddingModel embeddingModel;
    private final CircuitBreaker embeddingCircuitBreaker;
    private final Bulkhead embeddingBulkhead;

    public EmbeddingService(EmbeddingModel embeddingModel,
                            @Qualifier("embeddingCircuitBreaker") CircuitBreaker embeddingCircuitBreaker,
                            @Qualifier("embeddingBulkhead") Bulkhead embeddingBulkhead) {
        this.embeddingModel = embeddingModel;
        this.embeddingCircuitBreaker = embeddingCircuitBreaker;
        this.embeddingBulkhead = embeddingBulkhead;
    }

    public float[] generateEmbedding(String text) {
        Exception lastException = null;
        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            try {
                float[] result = callProtectedEmbedding(text);
                if (attempt > 1) log.info("Embedding succeeded on attempt {}", attempt);
                return result;
            } catch (CallNotPermittedException e) {
                log.error("Embedding circuit breaker is OPEN — halting ingestion");
                throw new RuntimeException("Embedding service unavailable (circuit breaker open)", e);
            } catch (BulkheadFullException e) {
                log.error("Embedding bulkhead full — too many concurrent ingestion threads");
                throw new RuntimeException("Embedding service busy (bulkhead full)", e);
            } catch (Exception e) {
                lastException = e;
                log.warn("Embedding attempt {}/{} failed: {}", attempt, MAX_ATTEMPTS, e.getMessage());
                if (attempt < MAX_ATTEMPTS) sleepBackoff(attempt);
            }
        }
        log.error("All {} embedding attempts failed", MAX_ATTEMPTS, lastException);
        throw new RuntimeException("Failed to generate embedding after " + MAX_ATTEMPTS + " attempts", lastException);
    }

    public List<float[]> generateEmbeddings(List<String> texts) {
        return texts.stream().map(this::generateEmbedding).toList();
    }

    private float[] callProtectedEmbedding(String text) {
        return embeddingBulkhead.executeSupplier(
            () -> embeddingCircuitBreaker.executeSupplier(() -> callEmbeddingOnce(text))
        );
    }

    private float[] callEmbeddingOnce(String text) {
        EmbeddingRequest request = new EmbeddingRequest(List.of(text), null);
        EmbeddingResponse response = embeddingModel.call(request);
        if (response.getResults().isEmpty()) {
            throw new RuntimeException("Embedding API returned empty result");
        }
        return response.getResult().getOutput();
    }

    private static void sleepBackoff(int attempt) {
        try {
            Thread.sleep(INITIAL_BACKOFF_MS * (1L << (attempt - 1)));
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Interrupted while waiting to retry embedding", ie);
        }
    }
}

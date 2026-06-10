package com.docai.ingestor.application.service;

import java.util.List;

import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.embedding.EmbeddingResponse;
import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class EmbeddingService {

    private static final int MAX_ATTEMPTS = 3;
    private static final long INITIAL_BACKOFF_MS = 1000L;

    private final EmbeddingModel embeddingModel;

    public float[] generateEmbedding(String text) {
        Exception lastException = null;
        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            try {
                EmbeddingRequest request = new EmbeddingRequest(List.of(text), null);
                EmbeddingResponse response = embeddingModel.call(request);
                if (response.getResults().isEmpty()) {
                    throw new RuntimeException("Embedding API returned empty result");
                }
                if (attempt > 1) {
                    log.info("Embedding succeeded on attempt {}", attempt);
                }
                return response.getResult().getOutput();
            } catch (Exception e) {
                lastException = e;
                log.warn("Embedding attempt {}/{} failed: {}", attempt, MAX_ATTEMPTS, e.getMessage());
                if (attempt < MAX_ATTEMPTS) {
                    try {
                        Thread.sleep(INITIAL_BACKOFF_MS * (1L << (attempt - 1)));
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        throw new RuntimeException("Interrupted while waiting to retry embedding", ie);
                    }
                }
            }
        }
        log.error("All {} embedding attempts failed", MAX_ATTEMPTS, lastException);
        throw new RuntimeException("Failed to generate embedding after " + MAX_ATTEMPTS + " attempts", lastException);
    }

    public List<float[]> generateEmbeddings(List<String> texts) {
        return texts.stream()
            .map(this::generateEmbedding)
            .toList();
    }
}

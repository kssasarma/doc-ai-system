package com.docai.ingestor.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.embedding.Embedding;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.embedding.EmbeddingResponse;

import com.docai.ingestor.application.service.EmbeddingService;

import io.github.resilience4j.bulkhead.Bulkhead;
import io.github.resilience4j.bulkhead.BulkheadConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;

@ExtendWith(MockitoExtension.class)
class EmbeddingServiceTest {

    @Mock
    EmbeddingModel embeddingModel;

    private CircuitBreaker circuitBreaker;
    private Bulkhead bulkhead;
    private EmbeddingService embeddingService;

    @BeforeEach
    void setUp() {
        // Circuit breaker needs 4 calls before evaluating failure rate — won't open during normal tests
        circuitBreaker = CircuitBreaker.of("test-embedding",
            CircuitBreakerConfig.custom()
                .failureRateThreshold(50)
                .slidingWindowSize(4)
                .minimumNumberOfCalls(4)
                .build());
        bulkhead = Bulkhead.of("test-embedding",
            BulkheadConfig.custom()
                .maxConcurrentCalls(10)
                .maxWaitDuration(Duration.ZERO)
                .build());
        embeddingService = new EmbeddingService(embeddingModel, circuitBreaker, bulkhead);
    }

    @Test
    void generateEmbedding_success_returnsFloatArray() {
        float[] expected = {0.1f, 0.2f, 0.3f};
        EmbeddingResponse response = mockResponse(expected);
        when(embeddingModel.call(any(EmbeddingRequest.class))).thenReturn(response);

        float[] result = embeddingService.generateEmbedding("hello world");

        assertThat(result).isEqualTo(expected);
        verify(embeddingModel, times(1)).call(any(EmbeddingRequest.class));
    }

    @Test
    void generateEmbedding_circuitBreakerOpen_throwsWithoutCallingModel() {
        circuitBreaker.transitionToOpenState();

        assertThatThrownBy(() -> embeddingService.generateEmbedding("text"))
            .isInstanceOf(RuntimeException.class)
            .hasMessageContaining("circuit breaker open");

        verify(embeddingModel, times(0)).call(any());
    }

    @Test
    void generateEmbedding_bulkheadFull_throwsWithoutCallingModel() {
        Bulkhead zeroPermits = Bulkhead.of("zero",
            BulkheadConfig.custom()
                .maxConcurrentCalls(0)
                .maxWaitDuration(Duration.ZERO)
                .build());
        EmbeddingService service = new EmbeddingService(embeddingModel, circuitBreaker, zeroPermits);

        assertThatThrownBy(() -> service.generateEmbedding("text"))
            .isInstanceOf(RuntimeException.class)
            .hasMessageContaining("bulkhead full");

        verify(embeddingModel, times(0)).call(any());
    }

    @Test
    @Timeout(10) // allows for the 1s backoff sleep on first failure
    void generateEmbedding_transientFailureThenSuccess_retriesAndReturns() {
        float[] expected = {0.5f, 0.6f};
        // Use a lenient circuit breaker that won't open on a single failure
        CircuitBreaker lenient = CircuitBreaker.of("lenient",
            CircuitBreakerConfig.custom()
                .failureRateThreshold(100)
                .slidingWindowSize(4)
                .minimumNumberOfCalls(4)
                .build());
        EmbeddingService service = new EmbeddingService(embeddingModel, lenient, bulkhead);

        EmbeddingResponse response = mockResponse(expected);
        when(embeddingModel.call(any(EmbeddingRequest.class)))
            .thenThrow(new RuntimeException("transient error"))
            .thenReturn(response);

        float[] result = service.generateEmbedding("text");

        assertThat(result).isEqualTo(expected);
        verify(embeddingModel, times(2)).call(any(EmbeddingRequest.class));
    }

    @Test
    void generateEmbeddings_multipleTexts_returnsAllEmbeddings() {
        float[] emb1 = {0.1f};
        float[] emb2 = {0.2f};
        EmbeddingResponse response1 = mockResponse(emb1);
        EmbeddingResponse response2 = mockResponse(emb2);
        when(embeddingModel.call(any(EmbeddingRequest.class)))
            .thenReturn(response1)
            .thenReturn(response2);

        List<float[]> results = embeddingService.generateEmbeddings(List.of("a", "b"));

        assertThat(results).hasSize(2);
        assertThat(results.get(0)).isEqualTo(emb1);
        assertThat(results.get(1)).isEqualTo(emb2);
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    @SuppressWarnings("unchecked")
    private static EmbeddingResponse mockResponse(float[] values) {
        Embedding embedding = mock(Embedding.class);
        when(embedding.getOutput()).thenReturn(values);
        EmbeddingResponse response = mock(EmbeddingResponse.class);
        when(response.getResults()).thenReturn(List.of(embedding));
        when(response.getResult()).thenReturn(embedding);
        return response;
    }
}

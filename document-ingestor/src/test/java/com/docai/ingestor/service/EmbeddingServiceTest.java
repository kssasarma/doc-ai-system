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
import java.util.Optional;
import java.util.UUID;

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
import com.docai.ingestor.application.service.SecretsCryptoService;
import com.docai.ingestor.domain.entity.TenantLlmConfig;
import com.docai.ingestor.domain.repository.TenantLlmConfigRepository;

import io.github.resilience4j.bulkhead.Bulkhead;
import io.github.resilience4j.bulkhead.BulkheadConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;

@ExtendWith(MockitoExtension.class)
class EmbeddingServiceTest {

    private static final UUID TENANT_ID = UUID.randomUUID();

    @Mock
    EmbeddingModel embeddingModel;

    @Mock
    TenantLlmConfigRepository tenantLlmConfigRepository;

    private CircuitBreaker circuitBreaker;
    private Bulkhead bulkhead;
    private EmbeddingService embeddingService;
    private final SecretsCryptoService cryptoService = new SecretsCryptoService("");

    /** The service builds one-off OpenAI clients from tenant config; this seam substitutes the
     * mocked client so no network is involved. */
    private EmbeddingService serviceWith(CircuitBreaker cb, Bulkhead bh) {
        EmbeddingService service = new EmbeddingService(cb, bh, tenantLlmConfigRepository, cryptoService) {
            @Override
            protected EmbeddingModel buildClient(String baseUrl, String apiKey) {
                return embeddingModel;
            }
        };
        // @Value defaults don't apply outside a Spring context — mirror application.yml's values
        org.springframework.test.util.ReflectionTestUtils.setField(service, "batchSize", 64);
        org.springframework.test.util.ReflectionTestUtils.setField(service, "maxBatchTokens", 4000);
        return service;
    }

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
        embeddingService = serviceWith(circuitBreaker, bulkhead);
    }

    private void stubTenantConfig() {
        TenantLlmConfig config = new TenantLlmConfig();
        config.setTenantId(TENANT_ID);
        config.setChatProvider("openai");
        config.setEmbeddingProvider("openai");
        config.setEmbeddingModel("text-embedding-3-small");
        config.setApiKeyEnc(cryptoService.encrypt("tenant-key"));
        when(tenantLlmConfigRepository.findByTenantId(TENANT_ID)).thenReturn(Optional.of(config));
    }

    // ── tenant configuration is mandatory — no platform fallback ──────────────

    @Test
    void generateEmbedding_noTenantConfig_throwsNotConfigured() {
        when(tenantLlmConfigRepository.findByTenantId(TENANT_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> embeddingService.generateEmbedding("text", TENANT_ID))
            .isInstanceOf(EmbeddingService.TenantLlmNotConfiguredException.class)
            .hasMessageContaining("AI is not configured");

        verify(embeddingModel, times(0)).call(any());
    }

    @Test
    void generateEmbedding_nullTenant_throwsNotConfigured() {
        assertThatThrownBy(() -> embeddingService.generateEmbedding("text", null))
            .isInstanceOf(EmbeddingService.TenantLlmNotConfiguredException.class);
    }

    @Test
    void generateEmbedding_configWithoutAnyKey_throwsNotConfigured() {
        TenantLlmConfig config = new TenantLlmConfig();
        config.setTenantId(TENANT_ID);
        config.setChatProvider("openai");
        config.setEmbeddingProvider("openai");
        config.setEmbeddingModel("text-embedding-3-small");
        when(tenantLlmConfigRepository.findByTenantId(TENANT_ID)).thenReturn(Optional.of(config));

        assertThatThrownBy(() -> embeddingService.generateEmbedding("text", TENANT_ID))
            .isInstanceOf(EmbeddingService.TenantLlmNotConfiguredException.class)
            .hasMessageContaining("No embedding API key");
    }

    @Test
    void generateEmbedding_chatKeyNotReusedAcrossDifferentProviders_throwsNotConfigured() {
        // Chat key exists but chat provider (anthropic) differs from the embedding provider
        // (openai) — the chat key must not be reused, and there is no platform key.
        TenantLlmConfig config = new TenantLlmConfig();
        config.setTenantId(TENANT_ID);
        config.setChatProvider("anthropic");
        config.setEmbeddingProvider("openai");
        config.setEmbeddingModel("text-embedding-3-small");
        config.setApiKeyEnc(cryptoService.encrypt("anthropic-chat-key"));
        when(tenantLlmConfigRepository.findByTenantId(TENANT_ID)).thenReturn(Optional.of(config));

        assertThatThrownBy(() -> embeddingService.generateEmbedding("text", TENANT_ID))
            .isInstanceOf(EmbeddingService.TenantLlmNotConfiguredException.class)
            .hasMessageContaining("No embedding API key");
    }

    @Test
    void generateEmbedding_dedicatedEmbeddingKey_isUsedEvenWhenProvidersDiffer() {
        TenantLlmConfig config = new TenantLlmConfig();
        config.setTenantId(TENANT_ID);
        config.setChatProvider("anthropic");
        config.setEmbeddingProvider("openai");
        config.setEmbeddingModel("text-embedding-3-small");
        config.setEmbeddingApiKeyEnc(cryptoService.encrypt("openai-embed-key"));
        when(tenantLlmConfigRepository.findByTenantId(TENANT_ID)).thenReturn(Optional.of(config));
        EmbeddingResponse response = mockResponse(new float[]{0.1f});
        when(embeddingModel.call(any(EmbeddingRequest.class))).thenReturn(response);

        EmbeddingService.TenantEmbeddingResult result = embeddingService.generateEmbedding("text", TENANT_ID);

        assertThat(result.modelUsed()).isEqualTo("text-embedding-3-small");
    }

    // ── happy path + resilience ───────────────────────────────────────────────

    @Test
    void generateEmbedding_success_returnsFloatArrayAndModel() {
        stubTenantConfig();
        float[] expected = {0.1f, 0.2f, 0.3f};
        EmbeddingResponse response = mockResponse(expected);
        when(embeddingModel.call(any(EmbeddingRequest.class))).thenReturn(response);

        EmbeddingService.TenantEmbeddingResult result = embeddingService.generateEmbedding("hello world", TENANT_ID);

        assertThat(result.embedding()).isEqualTo(expected);
        assertThat(result.modelUsed()).isEqualTo("text-embedding-3-small");
        verify(embeddingModel, times(1)).call(any(EmbeddingRequest.class));
    }

    @Test
    void generateEmbedding_circuitBreakerOpen_throwsWithoutCallingModel() {
        stubTenantConfig();
        circuitBreaker.transitionToOpenState();

        assertThatThrownBy(() -> embeddingService.generateEmbedding("text", TENANT_ID))
            .isInstanceOf(RuntimeException.class)
            .hasMessageContaining("circuit breaker open");

        verify(embeddingModel, times(0)).call(any());
    }

    @Test
    void generateEmbedding_bulkheadFull_throwsWithoutCallingModel() {
        stubTenantConfig();
        Bulkhead zeroPermits = Bulkhead.of("zero",
            BulkheadConfig.custom()
                .maxConcurrentCalls(0)
                .maxWaitDuration(Duration.ZERO)
                .build());
        EmbeddingService service = serviceWith(circuitBreaker, zeroPermits);

        assertThatThrownBy(() -> service.generateEmbedding("text", TENANT_ID))
            .isInstanceOf(RuntimeException.class)
            .hasMessageContaining("bulkhead full");

        verify(embeddingModel, times(0)).call(any());
    }

    @Test
    @Timeout(10) // allows for the 1s backoff sleep on first failure
    void generateEmbedding_transientFailureThenSuccess_retriesAndReturns() {
        stubTenantConfig();
        float[] expected = {0.5f, 0.6f};
        // Use a lenient circuit breaker that won't open on a single failure
        CircuitBreaker lenient = CircuitBreaker.of("lenient",
            CircuitBreakerConfig.custom()
                .failureRateThreshold(100)
                .slidingWindowSize(4)
                .minimumNumberOfCalls(4)
                .build());
        EmbeddingService service = serviceWith(lenient, bulkhead);

        EmbeddingResponse response = mockResponse(expected);
        when(embeddingModel.call(any(EmbeddingRequest.class)))
            .thenThrow(new RuntimeException("transient error"))
            .thenReturn(response);

        EmbeddingService.TenantEmbeddingResult result = service.generateEmbedding("text", TENANT_ID);

        assertThat(result.embedding()).isEqualTo(expected);
        verify(embeddingModel, times(2)).call(any(EmbeddingRequest.class));
    }

    @Test
    void generateEmbeddings_batch_returnsAllEmbeddings() {
        stubTenantConfig();
        float[] emb1 = {0.1f};
        float[] emb2 = {0.2f};
        EmbeddingResponse response = mockResponse(emb1, emb2);
        when(embeddingModel.call(any(EmbeddingRequest.class))).thenReturn(response);

        EmbeddingService.TenantEmbeddingBatchResult result =
            embeddingService.generateEmbeddings(List.of("a", "b"), TENANT_ID);

        assertThat(result.embeddings()).hasSize(2);
        assertThat(result.embeddings().get(0)).isEqualTo(emb1);
        assertThat(result.embeddings().get(1)).isEqualTo(emb2);
        assertThat(result.modelUsed()).isEqualTo("text-embedding-3-small");
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private static EmbeddingResponse mockResponse(float[]... vectors) {
        // lenient: single-text and batch paths read different accessors (getResult vs getResults)
        List<Embedding> embeddings = new java.util.ArrayList<>();
        for (float[] values : vectors) {
            Embedding embedding = mock(Embedding.class);
            org.mockito.Mockito.lenient().when(embedding.getOutput()).thenReturn(values);
            embeddings.add(embedding);
        }
        EmbeddingResponse response = mock(EmbeddingResponse.class);
        org.mockito.Mockito.lenient().when(response.getResults()).thenReturn(embeddings);
        org.mockito.Mockito.lenient().when(response.getResult()).thenReturn(embeddings.get(0));
        return response;
    }
}

package com.docai.ingestor.application.service;

import java.util.List;
import java.util.UUID;

import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.embedding.EmbeddingResponse;
import org.springframework.ai.openai.OpenAiEmbeddingModel;
import org.springframework.ai.openai.OpenAiEmbeddingOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.docai.ingestor.domain.entity.TenantLlmConfig;
import com.docai.ingestor.domain.repository.TenantLlmConfigRepository;

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
    private final TenantLlmConfigRepository tenantLlmConfigRepository;
    private final SecretsCryptoService cryptoService;

    @Value("${spring.ai.openai.base-url}")
    private String baseUrl;

    @Value("${spring.ai.openai.embedding.options.model}")
    private String platformDefaultModel;

    public EmbeddingService(EmbeddingModel embeddingModel,
                            @Qualifier("embeddingCircuitBreaker") CircuitBreaker embeddingCircuitBreaker,
                            @Qualifier("embeddingBulkhead") Bulkhead embeddingBulkhead,
                            TenantLlmConfigRepository tenantLlmConfigRepository,
                            SecretsCryptoService cryptoService) {
        this.embeddingModel = embeddingModel;
        this.embeddingCircuitBreaker = embeddingCircuitBreaker;
        this.embeddingBulkhead = embeddingBulkhead;
        this.tenantLlmConfigRepository = tenantLlmConfigRepository;
        this.cryptoService = cryptoService;
    }

    /** Platform-default embedding, no tenant context. Prefer {@link #generateEmbedding(String, UUID)}. */
    public float[] generateEmbedding(String text) {
        return withResilience(() -> callEmbeddingOnce(text, embeddingModel, null));
    }

    /** Result also reports which model actually produced the embedding, so the caller can persist
     * it on the document (see IngestionService) for later query-time reuse. */
    public record TenantEmbeddingResult(float[] embedding, String modelUsed) {}

    /**
     * Embeds using the tenant's configured embedding provider/key/model when one exists and
     * decrypts successfully; falls back to the platform default client+model otherwise (no
     * config row, no custom key, or a wrong/rotated SECRETS_ENCRYPTION_KEY — same
     * degrade-gracefully behavior as the bot's LLMRouter).
     */
    public TenantEmbeddingResult generateEmbedding(String text, UUID tenantId) {
        TenantLlmConfig config = tenantId != null
            ? tenantLlmConfigRepository.findByTenantId(tenantId).orElse(null) : null;

        if (config != null && config.getApiKeyEnc() != null && "openai".equalsIgnoreCase(config.getEmbeddingProvider())) {
            String apiKey = cryptoService.decrypt(config.getApiKeyEnc());
            if (apiKey != null) {
                EmbeddingModel tenantClient = tenantEmbeddingModel(apiKey);
                float[] result = withResilience(() -> callEmbeddingOnce(text, tenantClient, config.getEmbeddingModel()));
                return new TenantEmbeddingResult(result, config.getEmbeddingModel());
            }
        }
        float[] result = withResilience(() -> callEmbeddingOnce(text, embeddingModel, null));
        return new TenantEmbeddingResult(result, platformDefaultModel);
    }

    public List<float[]> generateEmbeddings(List<String> texts) {
        return texts.stream().map(this::generateEmbedding).toList();
    }

    private static final int BATCH_SIZE = 64;

    public record TenantEmbeddingBatchResult(List<float[]> embeddings, String modelUsed) {}

    /**
     * Batches {@code texts} into groups of {@value #BATCH_SIZE} (OpenAI's embeddings endpoint
     * accepts an array of inputs per call) so ingesting a large document costs a handful of
     * round-trips instead of one per chunk — the resilience wrappers (circuit breaker/bulkhead)
     * apply per batch, not per chunk, same as the single-text path. Same tenant-config resolution
     * as {@link #generateEmbedding(String, UUID)}: every chunk in the document uses the same
     * resolved client+model, since they're all being embedded for the same ingest.
     */
    public TenantEmbeddingBatchResult generateEmbeddings(List<String> texts, UUID tenantId) {
        TenantLlmConfig config = tenantId != null
            ? tenantLlmConfigRepository.findByTenantId(tenantId).orElse(null) : null;

        EmbeddingModel client = embeddingModel;
        String model = platformDefaultModel;
        if (config != null && config.getApiKeyEnc() != null && "openai".equalsIgnoreCase(config.getEmbeddingProvider())) {
            String apiKey = cryptoService.decrypt(config.getApiKeyEnc());
            if (apiKey != null) {
                client = tenantEmbeddingModel(apiKey);
                model = config.getEmbeddingModel();
            }
        }

        List<float[]> results = new java.util.ArrayList<>(texts.size());
        EmbeddingModel finalClient = client;
        String finalModel = model;
        for (int start = 0; start < texts.size(); start += BATCH_SIZE) {
            List<String> batch = texts.subList(start, Math.min(start + BATCH_SIZE, texts.size()));
            results.addAll(withResilience(() -> callEmbeddingBatch(batch, finalClient, finalModel)));
        }
        return new TenantEmbeddingBatchResult(results, model);
    }

    private List<float[]> callEmbeddingBatch(List<String> batch, EmbeddingModel client, String model) {
        EmbeddingRequest request = new EmbeddingRequest(batch, OpenAiEmbeddingOptions.builder().model(model).build());
        EmbeddingResponse response = client.call(request);
        if (response.getResults().size() != batch.size()) {
            throw new RuntimeException("Embedding API returned " + response.getResults().size()
                + " results for a batch of " + batch.size() + " inputs");
        }
        return response.getResults().stream().map(org.springframework.ai.embedding.Embedding::getOutput).toList();
    }

    private <T> T withResilience(java.util.function.Supplier<T> call) {
        Exception lastException = null;
        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            try {
                T result = embeddingBulkhead.executeSupplier(
                    () -> embeddingCircuitBreaker.executeSupplier(call));
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

    /** {@code model == null} lets the client use its own pre-configured default (the platform
     * client's Spring AI autoconfiguration already bakes in {@code platformDefaultModel} — passing
     * it again as an explicit option here would be redundant, not more correct). A tenant-specific
     * one-off client has no such baked-in default, so its caller always supplies a model. */
    private float[] callEmbeddingOnce(String text, EmbeddingModel client, String model) {
        EmbeddingRequest request = model != null
            ? new EmbeddingRequest(List.of(text), OpenAiEmbeddingOptions.builder().model(model).build())
            : new EmbeddingRequest(List.of(text), null);
        EmbeddingResponse response = client.call(request);
        if (response.getResults().isEmpty()) {
            throw new RuntimeException("Embedding API returned empty result");
        }
        return response.getResult().getOutput();
    }

    /** Builds a one-off client bound to a tenant's own (decrypted) API key — same pattern as
     * documentation-bot's OpenAILLMProvider#tenantEmbeddingModel. Not cached: building one is
     * cheap (no network call), and always-fresh avoids any stale-key-after-rotation risk. */
    private OpenAiEmbeddingModel tenantEmbeddingModel(String apiKey) {
        OpenAiApi api = OpenAiApi.builder().baseUrl(baseUrl).apiKey(apiKey).build();
        return new OpenAiEmbeddingModel(api);
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

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

/**
 * Embeds chunks using the owning tenant's configuration (provider key, endpoint, model), read
 * from the bot-owned {@code tenant_llm_configs} table. There is no platform-level OpenAI key or
 * client anymore — a tenant without a complete embedding configuration cannot ingest documents,
 * and the resulting {@link TenantLlmNotConfiguredException} carries the message shown on the
 * failed document so the tenant's admin knows to finish the AI settings.
 */
@Slf4j
@Service
public class EmbeddingService {

    private static final int MAX_ATTEMPTS = 3;
    private static final long INITIAL_BACKOFF_MS = 1000L;
    private static final String DEFAULT_OPENAI_BASE_URL = "https://api.openai.com";

    /** Thrown when a tenant tries to ingest without a usable embedding configuration. */
    public static class TenantLlmNotConfiguredException extends RuntimeException {
        public TenantLlmNotConfiguredException(String message) {
            super(message);
        }
    }

    private final CircuitBreaker embeddingCircuitBreaker;
    private final Bulkhead embeddingBulkhead;
    private final TenantLlmConfigRepository tenantLlmConfigRepository;
    private final SecretsCryptoService cryptoService;

    @Value("${ingestor.embedding-batch-size:64}")
    private int batchSize;

    @Value("${ingestor.embedding-batch-max-tokens:4000}")
    private int maxBatchTokens;

    public EmbeddingService(@Qualifier("embeddingCircuitBreaker") CircuitBreaker embeddingCircuitBreaker,
                            @Qualifier("embeddingBulkhead") Bulkhead embeddingBulkhead,
                            TenantLlmConfigRepository tenantLlmConfigRepository,
                            SecretsCryptoService cryptoService) {
        this.embeddingCircuitBreaker = embeddingCircuitBreaker;
        this.embeddingBulkhead = embeddingBulkhead;
        this.tenantLlmConfigRepository = tenantLlmConfigRepository;
        this.cryptoService = cryptoService;
    }

    /** Result also reports which model actually produced the embedding, so the caller can persist
     * it on the document (see IngestionService) for later query-time reuse. */
    public record TenantEmbeddingResult(float[] embedding, String modelUsed) {}

    /** Embeds one text with the tenant's configured embedding client — see class docs. */
    public TenantEmbeddingResult generateEmbedding(String text, UUID tenantId) {
        ResolvedEmbeddingClient resolved = resolveClient(tenantId);
        float[] result = withResilience(() -> callEmbeddingOnce(text, resolved.client(), resolved.model()));
        return new TenantEmbeddingResult(result, resolved.model());
    }

    public record TenantEmbeddingBatchResult(List<float[]> embeddings, String modelUsed) {}

    /**
     * Batches {@code texts} into groups bounded by both {@code ingestor.embedding-batch-size}
     * inputs and a max-tokens-per-request ceiling (OpenAI-compatible embeddings endpoints accept
     * an array of inputs per call, but count tokens across the whole request against the model's
     * context window — e.g. snowflake-arctic's 8192 — so batching purely by count can still
     * overflow it once chunks average more than a couple hundred tokens) so ingesting a large
     * document costs a handful of round-trips instead of one per chunk — the resilience wrappers
     * (circuit breaker/bulkhead) apply per batch, not per chunk, same as the single-text path.
     * Every chunk in the document uses the same resolved tenant client+model, since they're all
     * being embedded for the same ingest. The token ceiling is also tenant-configured: a tenant's
     * {@code max_embedding_batch_tokens} (set by their admin to match their embedding model's
     * context window) takes precedence over this service's built-in default.
     */
    public TenantEmbeddingBatchResult generateEmbeddings(List<String> texts, UUID tenantId) {
        ResolvedEmbeddingClient resolved = resolveClient(tenantId);
        int effectiveMaxBatchTokens = resolved.maxBatchTokens();

        List<float[]> results = new java.util.ArrayList<>(texts.size());
        int start = 0;
        while (start < texts.size()) {
            int end = nextBatchEnd(texts, start, effectiveMaxBatchTokens);
            List<String> batch = texts.subList(start, end);
            results.addAll(withResilience(() -> callEmbeddingBatch(batch, resolved.client(), resolved.model())));
            start = end;
        }
        return new TenantEmbeddingBatchResult(results, resolved.model());
    }

    private record ResolvedEmbeddingClient(EmbeddingModel client, String model, int maxBatchTokens) {}

    /**
     * Resolves the tenant's embedding client entirely from its own config: dedicated embedding key
     * first, else the chat key when chat and embedding share a provider. Missing config, missing
     * key, an undecryptable key (wrong/rotated SECRETS_ENCRYPTION_KEY), or a provider without an
     * embedding API all fail ingestion with an actionable message — never a silent fallback to a
     * platform key, which no longer exists.
     */
    private ResolvedEmbeddingClient resolveClient(UUID tenantId) {
        TenantLlmConfig config = tenantId != null
            ? tenantLlmConfigRepository.findByTenantId(tenantId).orElse(null) : null;
        if (config == null) {
            throw new TenantLlmNotConfiguredException(
                "AI is not configured for this tenant — an admin must complete the AI settings "
                    + "(embedding provider, model and API key) before documents can be ingested");
        }
        if (!"openai".equalsIgnoreCase(config.getEmbeddingProvider())) {
            throw new TenantLlmNotConfiguredException(
                "Embedding provider '" + config.getEmbeddingProvider() + "' has no embedding API — "
                    + "this tenant's admin must select 'openai' (or an OpenAI-compatible endpoint "
                    + "via the embedding base URL) in the AI settings");
        }
        String keyEnc = config.getEmbeddingApiKeyEnc() != null && !config.getEmbeddingApiKeyEnc().isBlank()
            ? config.getEmbeddingApiKeyEnc()
            : (config.getEmbeddingProvider() != null
                && config.getEmbeddingProvider().equalsIgnoreCase(config.getChatProvider())
                ? config.getApiKeyEnc() : null);
        String apiKey = keyEnc != null && !keyEnc.isBlank() ? cryptoService.decrypt(keyEnc) : null;
        if (apiKey == null) {
            throw new TenantLlmNotConfiguredException(
                "No embedding API key is configured for this tenant — an admin must save one in "
                    + "the AI settings before documents can be ingested");
        }
        String baseUrl = config.getEmbeddingBaseUrl() != null && !config.getEmbeddingBaseUrl().isBlank()
            ? config.getEmbeddingBaseUrl() : DEFAULT_OPENAI_BASE_URL;
        int effectiveMaxBatchTokens = config.getMaxEmbeddingBatchTokens() != null
            && config.getMaxEmbeddingBatchTokens() > 0
            ? config.getMaxEmbeddingBatchTokens() : maxBatchTokens;

        return new ResolvedEmbeddingClient(buildClient(baseUrl, apiKey),
            config.getEmbeddingModel(), effectiveMaxBatchTokens);
    }

    /** Builds a one-off client bound to the tenant's key/endpoint. Not cached — building one is
     * cheap (no network call), and always-fresh avoids any stale-key-after-rotation risk.
     * Protected so tests can substitute a mock client. */
    protected EmbeddingModel buildClient(String baseUrl, String apiKey) {
        OpenAiApi api = OpenAiApi.builder().baseUrl(baseUrl).apiKey(apiKey).build();
        return new OpenAiEmbeddingModel(api);
    }

    /** Grows the batch from {@code start} while staying within {@code ingestor.embedding-batch-size}
     * inputs and {@code effectiveMaxBatchTokens} cumulative estimated tokens. Always includes at
     * least one input (even an oversized one alone) so a single very large chunk can't stall the
     * loop. */
    private int nextBatchEnd(List<String> texts, int start, int effectiveMaxBatchTokens) {
        int end = start;
        int tokenBudget = 0;
        while (end < texts.size()) {
            if (end > start && (end - start) >= batchSize) break;
            int tokens = SemanticChunker.estimateTokens(texts.get(end));
            if (end > start && tokenBudget + tokens > effectiveMaxBatchTokens) break;
            tokenBudget += tokens;
            end++;
        }
        return end;
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

    private float[] callEmbeddingOnce(String text, EmbeddingModel client, String model) {
        EmbeddingRequest request = new EmbeddingRequest(List.of(text),
            OpenAiEmbeddingOptions.builder().model(model).build());
        EmbeddingResponse response = client.call(request);
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

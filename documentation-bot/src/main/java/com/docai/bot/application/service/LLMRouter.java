package com.docai.bot.application.service;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.docai.bot.config.TenantContext;
import com.docai.bot.domain.entity.TenantLLMConfig;
import com.docai.bot.domain.repository.TenantLLMConfigRepository;

import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;

/**
 * Central routing point for all LLM calls — the single seam every answer-generating service
 * (AnswerGenerationService, MultiHopReasoningService, ReRankingService, QueryAnalyzerService,
 * ChatSummaryService, AnswerEvolutionService, VersionDiffService, DocumentationGapService) must
 * go through instead of holding their own {@code ChatClient.Builder}.
 *
 * Routing logic (per request):
 *   1. Look up the current tenant's TenantLLMConfig.
 *   2. If routing is enabled: route simple queries to simpleModel, complex to complexModel.
 *   3. Dispatch to the matching LLMProvider (openai, anthropic), using the tenant's own
 *      (decrypted) API key when configured — else the platform default key.
 *   4. Falls back to the global default (OpenAI, platform key) if the tenant's provider throws
 *      for any reason (bad/missing key, network error, rate limit) — a misconfigured or
 *      temporarily-broken BYO key degrades gracefully rather than failing the user's request.
 */
@Slf4j
@Service
public class LLMRouter {

    /** Content + token usage for a single chat completion — mirrors what callers previously
     * pulled out of a raw ChatResponse themselves. */
    public record LlmChatResult(String content, int promptTokens, int completionTokens) {}

    private final Map<String, LLMProvider> providers;
    private final TenantLLMConfigRepository configRepository;
    private final SecretsCryptoService cryptoService;
    private final String defaultChatModel;
    private final String defaultEmbeddingModel;

    public LLMRouter(List<LLMProvider> providers,
                     TenantLLMConfigRepository configRepository,
                     SecretsCryptoService cryptoService,
                     @Value("${spring.ai.openai.chat.options.model:gpt-4o-mini}") String defaultChatModel,
                     @Value("${spring.ai.openai.embedding.options.model:text-embedding-3-small}") String defaultEmbeddingModel) {
        this.providers           = providers.stream()
            .collect(Collectors.toMap(LLMProvider::providerName, Function.identity()));
        this.configRepository    = configRepository;
        this.cryptoService       = cryptoService;
        this.defaultChatModel    = defaultChatModel;
        this.defaultEmbeddingModel = defaultEmbeddingModel;
    }

    /** Convenience overload — assumes simple query, returns only the answer text. */
    public String chat(String userMessage, boolean complexQuery) {
        return chatWithUsage(userMessage, complexQuery).content();
    }

    /**
     * Route a chat completion request for the current tenant (from {@link TenantContext}),
     * returning content + token usage.
     *
     * @param userMessage   user query (no separate system prompt — every current call site
     *                      builds one combined prompt string)
     * @param complexQuery  true → use the complex/expensive model when tenant routing is on
     */
    public LlmChatResult chatWithUsage(String userMessage, boolean complexQuery) {
        TenantLLMConfig config = tenantConfig().orElse(null);
        String providerName = config != null ? config.getChatProvider() : "openai";
        String model;
        if (config != null && config.isRoutingEnabled()) {
            model = complexQuery ? config.getComplexModel() : config.getSimpleModel();
        } else {
            model = config != null ? config.getChatModel() : defaultChatModel;
        }
        String apiKey = decryptedKey(config);

        LLMProvider provider = providers.getOrDefault(providerName, providers.get("openai"));
        if (!providers.containsKey(providerName)) {
            log.warn("LLMRouter: tenant configured unknown chat provider '{}' — falling back to openai. "
                + "This should be unreachable now that TenantService validates provider names at save time.", providerName);
        }
        log.debug("LLMRouter chat provider={} model={} complex={} customKey={}",
            providerName, model, complexQuery, apiKey != null);

        try {
            return toResult(provider.chat(null, userMessage, model, apiKey));
        } catch (Exception primary) {
            log.warn("LLMRouter: provider {} (model {}) failed — falling back to platform openai/{}: {}",
                providerName, model, defaultChatModel, primary.getMessage());
            return toResult(providers.get("openai").chat(null, userMessage, defaultChatModel, null));
        }
    }

    /**
     * Streaming counterpart of {@link #chatWithUsage} — same tenant/provider/model/key
     * resolution, but returns text deltas as the model generates them instead of blocking for the
     * full completion. Tenant resolution happens synchronously here (before the Flux is
     * returned/subscribed), so it's safe to call from a request thread even though the actual
     * network streaming happens later, off-thread, once something subscribes.
     *
     * Falls back to the platform default on any error the *primary* provider's stream raises
     * (e.g. an invalid BYO key rejected on connect) — same graceful-degradation contract as the
     * blocking path. This only catches errors the stream surfaces through its error channel, not
     * partial-then-broken streams (rare in practice: provider auth/rate-limit failures happen at
     * connection time, not mid-token-stream).
     */
    public Flux<String> streamChat(String userMessage, boolean complexQuery) {
        TenantLLMConfig config = tenantConfig().orElse(null);
        String providerName = config != null ? config.getChatProvider() : "openai";
        String model;
        if (config != null && config.isRoutingEnabled()) {
            model = complexQuery ? config.getComplexModel() : config.getSimpleModel();
        } else {
            model = config != null ? config.getChatModel() : defaultChatModel;
        }
        String apiKey = decryptedKey(config);

        LLMProvider provider = providers.getOrDefault(providerName, providers.get("openai"));
        log.debug("LLMRouter streamChat provider={} model={} complex={} customKey={}",
            providerName, model, complexQuery, apiKey != null);

        return provider.stream(null, userMessage, model, apiKey)
            .onErrorResume(primary -> {
                log.warn("LLMRouter: streaming provider {} (model {}) failed — falling back to platform openai/{}: {}",
                    providerName, model, defaultChatModel, primary.getMessage());
                return providers.get("openai").stream(null, userMessage, defaultChatModel, null);
            });
    }

    /**
     * Route an embedding request for the current tenant.
     * Embedding always uses the tenant's configured embedding provider (default OpenAI) — see
     * TenantLLMConfig.embeddingModel. Callers that must match an already-ingested document's
     * embedding model (rather than the tenant's current config) should pass that model explicitly
     * via {@link #embed(String, String)}.
     */
    public List<Double> embed(String text) {
        TenantLLMConfig config = tenantConfig().orElse(null);
        String model = config != null ? config.getEmbeddingModel() : defaultEmbeddingModel;
        return embed(text, model);
    }

    /** Embeds using an explicit model (e.g. the model recorded on the documents being searched)
     * rather than the tenant's current embedding config, which may have changed since ingestion. */
    public List<Double> embed(String text, String model) {
        TenantLLMConfig config = tenantConfig().orElse(null);
        String providerName = config != null ? config.getEmbeddingProvider() : "openai";
        String apiKey = decryptedKey(config);

        LLMProvider provider = providers.getOrDefault(providerName, providers.get("openai"));
        if (!providers.containsKey(providerName)) {
            log.warn("LLMRouter: tenant configured unknown embedding provider '{}' — falling back to openai. "
                + "This should be unreachable now that TenantService validates provider names at save time.", providerName);
        }
        try {
            return provider.embed(text, model, apiKey);
        } catch (Exception e) {
            log.warn("Embedding provider {} failed, falling back to platform openai/{}: {}",
                providerName, defaultEmbeddingModel, e.getMessage());
            return providers.get("openai").embed(text, defaultEmbeddingModel, null);
        }
    }

    private String decryptedKey(TenantLLMConfig config) {
        if (config == null || config.getApiKeyEnc() == null || config.getApiKeyEnc().isBlank()) return null;
        return cryptoService.decrypt(config.getApiKeyEnc());
    }

    private static LlmChatResult toResult(ChatResponse response) {
        String content = response != null && response.getResult() != null
            ? response.getResult().getOutput().getText() : null;
        int promptTokens = 0, completionTokens = 0;
        try {
            if (response != null && response.getMetadata() != null && response.getMetadata().getUsage() != null) {
                var usage = response.getMetadata().getUsage();
                if (usage.getPromptTokens() != null) promptTokens = usage.getPromptTokens().intValue();
                if (usage.getCompletionTokens() != null) completionTokens = usage.getCompletionTokens().intValue();
            }
        } catch (Exception ignored) {
            // Usage metadata is best-effort; callers already estimate tokens from text length
            // when it's unavailable (see AnswerGenerationService/MultiHopReasoningService).
        }
        return new LlmChatResult(content, promptTokens, completionTokens);
    }

    private Optional<TenantLLMConfig> tenantConfig() {
        UUID tenantId = TenantContext.get();
        return configRepository.findByTenantId(tenantId);
    }
}

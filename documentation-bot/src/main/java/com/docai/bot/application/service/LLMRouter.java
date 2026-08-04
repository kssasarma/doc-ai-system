package com.docai.bot.application.service;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.ai.chat.model.ChatResponse;
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
 * Every setting an LLM call needs — provider, model, API key, endpoint, generation options — comes
 * from the current tenant's TenantLLMConfig, configured by that tenant's admin. There is no
 * platform-level key, endpoint, or model to fall back to: a tenant whose config is missing or has
 * no API key gets a {@link LlmNotConfiguredException} (HTTP 422, code LLM_NOT_CONFIGURED) telling
 * its admin to complete the AI settings.
 *
 * Routing logic (per request):
 *   1. Load the current tenant's TenantLLMConfig — absent/keyless config fails fast.
 *   2. If routing is enabled: route simple queries to simpleModel, complex to complexModel.
 *   3. Dispatch to the matching LLMProvider (openai, anthropic) with the tenant's decrypted key,
 *      endpoint override, temperature and max-tokens.
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

    public LLMRouter(List<LLMProvider> providers,
                     TenantLLMConfigRepository configRepository,
                     SecretsCryptoService cryptoService) {
        this.providers        = providers.stream()
            .collect(Collectors.toMap(LLMProvider::providerName, Function.identity()));
        this.configRepository = configRepository;
        this.cryptoService    = cryptoService;
    }

    /** Convenience overload — returns only the answer text. */
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
        ChatDispatch dispatch = resolveChat(complexQuery);
        log.debug("LLMRouter chat provider={} model={} complex={}",
            dispatch.providerName, dispatch.settings.model(), complexQuery);
        return toResult(dispatch.provider.chat(null, userMessage, dispatch.settings));
    }

    /** Dedicated entry point for {@link ReRankingService}'s LLM re-rank pass — routed via
     * {@link #resolveRerankChat()} rather than the simple/complex split {@link #chatWithUsage}
     * uses, so a tenant can point re-ranking at its own model. */
    public String chatForRerank(String userMessage) {
        ChatDispatch dispatch = resolveRerankChat();
        log.debug("LLMRouter chat provider={} model={} purpose=rerank",
            dispatch.providerName, dispatch.settings.model());
        return toResult(dispatch.provider.chat(null, userMessage, dispatch.settings)).content();
    }

    /**
     * Streaming counterpart of {@link #chatWithUsage} — same tenant/provider/model/key
     * resolution, but returns text deltas as the model generates them instead of blocking for the
     * full completion. Tenant resolution happens synchronously here (before the Flux is
     * returned/subscribed), so it's safe to call from a request thread even though the actual
     * network streaming happens later, off-thread, once something subscribes.
     */
    public Flux<String> streamChat(String userMessage, boolean complexQuery) {
        ChatDispatch dispatch = resolveChat(complexQuery);
        log.debug("LLMRouter streamChat provider={} model={} complex={}",
            dispatch.providerName, dispatch.settings.model(), complexQuery);
        return dispatch.provider.stream(null, userMessage, dispatch.settings);
    }

    /**
     * Route an embedding request for the current tenant, using the tenant's configured embedding
     * model. Callers that must match an already-ingested document's embedding model (rather than
     * the tenant's current config) should pass that model explicitly via {@link #embed(String, String)}.
     */
    public List<Double> embed(String text) {
        return embed(text, requireConfig().getEmbeddingModel());
    }

    /** Embeds using an explicit model (e.g. the model recorded on the documents being searched)
     * rather than the tenant's current embedding config, which may have changed since ingestion. */
    public List<Double> embed(String text, String model) {
        TenantLLMConfig config = requireConfig();
        String providerName = config.getEmbeddingProvider();
        LLMProvider provider = requireProvider(providerName, "embedding");
        if (!provider.supportsEmbeddings()) {
            throw new LlmNotConfiguredException("Embedding provider '" + providerName
                + "' cannot serve embeddings — this tenant's admin must select a provider with an "
                + "embedding API (e.g. openai) in the AI settings");
        }
        return provider.embed(text, new LLMProvider.EmbedSettings(
            model, requireEmbeddingKey(config), config.getEmbeddingBaseUrl()));
    }

    private record ChatDispatch(String providerName, LLMProvider provider, LLMProvider.ChatSettings settings) {}

    private ChatDispatch resolveChat(boolean complexQuery) {
        TenantLLMConfig config = requireConfig();
        String model = config.isRoutingEnabled()
            ? (complexQuery ? config.getComplexModel() : config.getSimpleModel())
            : config.getChatModel();
        return dispatchFor(config, model);
    }

    /** Resolves the model for {@link ReRankingService}'s LLM re-rank pass: the tenant's dedicated
     * {@code rerankModel} when set, else the same model "simple" chat traffic uses — never the
     * complex/expensive tier, since re-ranking is a cheap relevance judgment, not answer
     * synthesis. */
    private ChatDispatch resolveRerankChat() {
        TenantLLMConfig config = requireConfig();
        String model = hasText(config.getRerankModel())
            ? config.getRerankModel()
            : (config.isRoutingEnabled() ? config.getSimpleModel() : config.getChatModel());
        return dispatchFor(config, model);
    }

    private ChatDispatch dispatchFor(TenantLLMConfig config, String model) {
        String providerName = config.getChatProvider();
        LLMProvider provider = requireProvider(providerName, "chat");
        String apiKey = decrypt(config.getApiKeyEnc());
        if (apiKey == null) {
            throw new LlmNotConfiguredException(
                "No API key is configured for this tenant's chat provider ('" + providerName
                    + "') — an admin must save one in the AI settings before AI features can be used");
        }
        return new ChatDispatch(providerName, provider, new LLMProvider.ChatSettings(
            model, apiKey, config.getChatBaseUrl(), config.getTemperature(), config.getMaxTokens()));
    }

    private static boolean hasText(String s) {
        return s != null && !s.isBlank();
    }

    /** The tenant's embedding key: its dedicated embedding key, else the chat key when both sides
     * use the same provider. Never a platform key — none exists. */
    private String requireEmbeddingKey(TenantLLMConfig config) {
        String key = decrypt(config.getEmbeddingApiKeyEnc());
        if (key == null && config.getEmbeddingProvider() != null
            && config.getEmbeddingProvider().equalsIgnoreCase(config.getChatProvider())) {
            key = decrypt(config.getApiKeyEnc());
        }
        if (key == null) {
            throw new LlmNotConfiguredException(
                "No API key is configured for this tenant's embedding provider ('"
                    + config.getEmbeddingProvider() + "') — an admin must save one in the AI settings");
        }
        return key;
    }

    private LLMProvider requireProvider(String providerName, String kind) {
        LLMProvider provider = providers.get(providerName);
        if (provider == null) {
            // Should be unreachable now that TenantService validates provider names at save time,
            // but a row written before that validation existed could still carry a bad name.
            throw new LlmNotConfiguredException("Unknown " + kind + " provider '" + providerName
                + "' configured for this tenant — must be one of " + providers.keySet());
        }
        return provider;
    }

    private String decrypt(String encrypted) {
        if (encrypted == null || encrypted.isBlank()) return null;
        return cryptoService.decrypt(encrypted);
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

    private TenantLLMConfig requireConfig() {
        UUID tenantId = TenantContext.get();
        return configRepository.findByTenantId(tenantId)
            .orElseThrow(() -> new LlmNotConfiguredException(
                "AI is not configured for this tenant — an admin must complete the AI settings "
                    + "(provider, model and API key) before AI features can be used"));
    }
}

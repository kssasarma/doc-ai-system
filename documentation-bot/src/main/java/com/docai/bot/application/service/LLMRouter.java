package com.docai.bot.application.service;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.docai.bot.config.TenantContext;
import com.docai.bot.domain.entity.TenantLLMConfig;
import com.docai.bot.domain.repository.TenantLLMConfigRepository;

import lombok.extern.slf4j.Slf4j;

/**
 * Central routing point for all LLM calls.
 *
 * Routing logic (per request):
 *   1. Look up the current tenant's TenantLLMConfig.
 *   2. If routing is enabled: route simple queries to simpleModel, complex to complexModel.
 *   3. Dispatch to the matching LLMProvider (openai, anthropic).
 *   4. Falls back to the global default (OpenAI) if no tenant config exists.
 */
@Slf4j
@Service
public class LLMRouter {

    private final Map<String, LLMProvider> providers;
    private final TenantLLMConfigRepository configRepository;
    private final String defaultChatModel;
    private final String defaultEmbeddingModel;

    public LLMRouter(List<LLMProvider> providers,
                     TenantLLMConfigRepository configRepository,
                     @Value("${spring.ai.openai.chat.options.model:gpt-4o-mini}") String defaultChatModel,
                     @Value("${spring.ai.openai.embedding.options.model:gpt-4o-embedding-4k}") String defaultEmbeddingModel) {
        this.providers           = providers.stream()
            .collect(Collectors.toMap(LLMProvider::providerName, Function.identity()));
        this.configRepository    = configRepository;
        this.defaultChatModel    = defaultChatModel;
        this.defaultEmbeddingModel = defaultEmbeddingModel;
    }

    /**
     * Route a chat completion request.
     *
     * @param systemPrompt  system instruction
     * @param userMessage   user query
     * @param complexQuery  true → use the complex/expensive model when routing is on
     * @return assistant response text
     */
    public String chat(String systemPrompt, String userMessage, boolean complexQuery) {
        TenantLLMConfig config = tenantConfig().orElse(null);
        String providerName = config != null ? config.getChatProvider() : "openai";
        String model;
        if (config != null && config.isRoutingEnabled()) {
            model = complexQuery ? config.getComplexModel() : config.getSimpleModel();
        } else {
            model = config != null ? config.getChatModel() : defaultChatModel;
        }

        LLMProvider provider = providers.getOrDefault(providerName, providers.get("openai"));
        log.debug("LLMRouter chat provider={} model={} complex={}", providerName, model, complexQuery);

        try {
            return provider.chat(systemPrompt, userMessage, model);
        } catch (Exception primary) {
            log.warn("LLMRouter primary provider {} failed, falling back to openai: {}", providerName, primary.getMessage());
            return providers.get("openai").chat(systemPrompt, userMessage, defaultChatModel);
        }
    }

    /** Convenience overload — assumes simple query. */
    public String chat(String systemPrompt, String userMessage) {
        return chat(systemPrompt, userMessage, false);
    }

    /**
     * Route an embedding request.
     * Embedding always uses the configured embedding provider (default OpenAI).
     */
    public List<Double> embed(String text) {
        TenantLLMConfig config = tenantConfig().orElse(null);
        String providerName = config != null ? config.getEmbeddingProvider() : "openai";
        String model = config != null ? config.getEmbeddingModel() : defaultEmbeddingModel;

        LLMProvider provider = providers.getOrDefault(providerName, providers.get("openai"));
        try {
            return provider.embed(text, model);
        } catch (Exception e) {
            log.warn("Embedding provider {} failed, falling back to openai: {}", providerName, e.getMessage());
            return providers.get("openai").embed(text, defaultEmbeddingModel);
        }
    }

    private Optional<TenantLLMConfig> tenantConfig() {
        UUID tenantId = TenantContext.get();
        return configRepository.findByTenantId(tenantId);
    }
}

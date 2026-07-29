package com.docai.bot.application.service;

import java.util.List;

import org.springframework.ai.chat.model.ChatResponse;

import reactor.core.publisher.Flux;

/**
 * Abstraction over any LLM backend.
 * Implementations: OpenAILLMProvider, AnthropicLLMProvider.
 * The LLMRouter selects the appropriate provider at call time and supplies the tenant's own
 * settings (API key, endpoint, model, generation options). There is no platform-level key or
 * client anymore — every call is made with credentials the tenant's admin configured, so
 * {@code apiKey} is required on every settings object.
 */
public interface LLMProvider {

    /** Returns the provider name, e.g. "openai", "anthropic" — must match a registered bean's
     * name for {@link LLMRouter}/{@link TenantService#updateLLMConfig} to accept it. */
    String providerName();

    /**
     * Everything a chat call needs, resolved from the tenant's config by {@link LLMRouter}.
     *
     * @param model       model ID (e.g. "gpt-4o-mini", "claude-haiku-4-5-20251001")
     * @param apiKey      the tenant's decrypted API key — required, never a platform key
     * @param baseUrl     endpoint override; null/blank = the provider's canonical public endpoint
     * @param temperature sampling temperature
     * @param maxTokens   completion token ceiling
     */
    record ChatSettings(String model, String apiKey, String baseUrl, double temperature, int maxTokens) {}

    /** Everything an embedding call needs — same key semantics as {@link ChatSettings}. */
    record EmbedSettings(String model, String apiKey, String baseUrl) {}

    /**
     * Synchronous chat completion using the tenant's settings.
     * Returns the full {@link ChatResponse} (not just text) so callers can read token usage.
     *
     * @param systemPrompt system instruction (nullable — omitted from the prompt when null/blank)
     * @param userMessage  user turn content
     */
    ChatResponse chat(String systemPrompt, String userMessage, ChatSettings settings);

    /** Streaming chat completion — emits text deltas as the model generates them. */
    Flux<String> stream(String systemPrompt, String userMessage, ChatSettings settings);

    /**
     * Produce an embedding vector for the given text.
     *
     * @return double list (e.g. length 1536 for text-embedding-3-small)
     */
    List<Double> embed(String text, EmbedSettings settings);

    /** Whether this provider can serve embedding requests at all (Anthropic cannot). */
    default boolean supportsEmbeddings() {
        return true;
    }
}

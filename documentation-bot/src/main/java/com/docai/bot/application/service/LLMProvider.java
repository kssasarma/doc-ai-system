package com.docai.bot.application.service;

import java.util.List;

import org.springframework.ai.chat.model.ChatResponse;

import reactor.core.publisher.Flux;

/**
 * Abstraction over any LLM backend.
 * Implementations: OpenAILLMProvider, AnthropicLLMProvider.
 * The LLMRouter selects the appropriate provider + model at call time.
 */
public interface LLMProvider {

    /** Returns the provider name, e.g. "openai", "anthropic" — must match a registered bean's
     * name for {@link LLMRouter}/{@link TenantService#updateLLMConfig} to accept it. */
    String providerName();

    /**
     * Synchronous chat completion using this provider's platform-configured (global) API key.
     * Returns the full {@link ChatResponse} (not just text) so callers can read token usage.
     *
     * @param systemPrompt  system instruction (nullable — omitted from the prompt when null/blank)
     * @param userMessage   user turn content
     * @param model         model ID to use (e.g. "gpt-4o-mini", "claude-haiku-4-5-20251001")
     */
    default ChatResponse chat(String systemPrompt, String userMessage, String model) {
        return chat(systemPrompt, userMessage, model, null);
    }

    /**
     * Synchronous chat completion, optionally overriding the API key for this single call —
     * used to route a tenant's own (decrypted) BYO key instead of the platform default.
     *
     * @param apiKeyOverride tenant-specific API key, or null to use the platform default.
     */
    ChatResponse chat(String systemPrompt, String userMessage, String model, String apiKeyOverride);

    /**
     * Produce an embedding vector for the given text, using the platform-configured API key.
     *
     * @param text  text to embed
     * @param model embedding model ID
     * @return float array (e.g. length 1536 for text-embedding-3-small)
     */
    default List<Double> embed(String text, String model) {
        return embed(text, model, null);
    }

    /** Same as {@link #embed(String, String)}, optionally overriding the API key. */
    List<Double> embed(String text, String model, String apiKeyOverride);

    /**
     * Streaming chat completion — emits text deltas as the model generates them, using the
     * platform-configured API key.
     */
    default Flux<String> stream(String systemPrompt, String userMessage, String model) {
        return stream(systemPrompt, userMessage, model, null);
    }

    /** Same as {@link #stream(String, String, String)}, optionally overriding the API key. */
    Flux<String> stream(String systemPrompt, String userMessage, String model, String apiKeyOverride);
}

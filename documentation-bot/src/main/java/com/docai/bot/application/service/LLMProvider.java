package com.docai.bot.application.service;

import java.util.List;

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
     * Synchronous chat completion.
     *
     * @param systemPrompt  system instruction
     * @param userMessage   user turn content
     * @param model         model ID to use (e.g. "gpt-4o-mini", "claude-haiku-4-5-20251001")
     * @return assistant response text
     */
    String chat(String systemPrompt, String userMessage, String model);

    /**
     * Produce an embedding vector for the given text.
     *
     * @param text  text to embed
     * @param model embedding model ID
     * @return float array (e.g. length 3072 for text-embedding-3-large)
     */
    List<Double> embed(String text, String model);
}

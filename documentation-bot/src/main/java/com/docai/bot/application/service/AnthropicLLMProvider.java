package com.docai.bot.application.service;

import java.util.List;

import org.springframework.ai.anthropic.AnthropicChatModel;
import org.springframework.ai.anthropic.AnthropicChatOptions;
import org.springframework.ai.anthropic.api.AnthropicApi;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;

/**
 * Anthropic Claude provider — active only when spring.ai.anthropic.api-key is set.
 * Embedding falls back to OpenAI (Anthropic does not offer a standalone embedding API).
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "spring.ai.anthropic.api-key", matchIfMissing = false)
@RequiredArgsConstructor
public class AnthropicLLMProvider implements LLMProvider {

    private final AnthropicChatModel anthropicChatModel;
    private final OpenAILLMProvider openAIProvider;   // Anthropic has no embedding API

    @Value("${spring.ai.anthropic.base-url:https://api.anthropic.com}")
    private String baseUrl;

    @Override
    public String providerName() {
        return "anthropic";
    }

    @Override
    public ChatResponse chat(String systemPrompt, String userMessage, String model, String apiKeyOverride) {
        Prompt prompt = buildPrompt(systemPrompt, userMessage, model);
        AnthropicChatModel client = apiKeyOverride != null ? tenantChatModel(apiKeyOverride) : anthropicChatModel;
        return client.call(prompt);
    }

    @Override
    public Flux<String> stream(String systemPrompt, String userMessage, String model, String apiKeyOverride) {
        Prompt prompt = buildPrompt(systemPrompt, userMessage, model);
        AnthropicChatModel client = apiKeyOverride != null ? tenantChatModel(apiKeyOverride) : anthropicChatModel;
        return client.stream(prompt)
            .mapNotNull(response -> response.getResult() != null && response.getResult().getOutput() != null
                ? response.getResult().getOutput().getText() : null)
            .filter(text -> !text.isEmpty());
    }

    private Prompt buildPrompt(String systemPrompt, String userMessage, String model) {
        List<org.springframework.ai.chat.messages.Message> messages = (systemPrompt == null || systemPrompt.isBlank())
            ? List.of(new UserMessage(userMessage))
            : List.of(new SystemMessage(systemPrompt), new UserMessage(userMessage));
        return new Prompt(messages, AnthropicChatOptions.builder().model(model).build());
    }

    @Override
    public List<Double> embed(String text, String model, String apiKeyOverride) {
        // Anthropic has no public embedding API — delegate to OpenAI (platform key; a tenant's
        // Anthropic key has nothing to authenticate here, and no per-tenant OpenAI key implies
        // no per-tenant embedding key either).
        log.debug("Anthropic embed delegating to OpenAI for embedding");
        return openAIProvider.embed(text, model, null);
    }

    /** Builds a one-off client bound to a tenant's own (decrypted) Anthropic key. Not cached — see
     * {@link OpenAILLMProvider#chat}'s equivalent for the same rationale. */
    private AnthropicChatModel tenantChatModel(String apiKey) {
        AnthropicApi api = AnthropicApi.builder().baseUrl(baseUrl).apiKey(apiKey).build();
        return AnthropicChatModel.builder().anthropicApi(api).build();
    }
}

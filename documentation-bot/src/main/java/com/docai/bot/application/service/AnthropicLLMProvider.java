package com.docai.bot.application.service;

import java.util.List;

import org.springframework.ai.anthropic.AnthropicChatModel;
import org.springframework.ai.anthropic.AnthropicChatOptions;
import org.springframework.ai.anthropic.api.AnthropicApi;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.stereotype.Component;

import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;

/**
 * Anthropic Claude provider. Always registered — whether a tenant can use it depends solely on
 * that tenant's own key saved in its LLM config, not on any platform-level ANTHROPIC_API_KEY
 * (which no longer exists). Anthropic has no embedding API, so tenants using Claude for chat
 * configure OpenAI (or compatible) as their embedding provider with its own key.
 */
@Slf4j
@Component
public class AnthropicLLMProvider implements LLMProvider {

    static final String DEFAULT_BASE_URL = "https://api.anthropic.com";

    @Override
    public String providerName() {
        return "anthropic";
    }

    @Override
    public ChatResponse chat(String systemPrompt, String userMessage, ChatSettings settings) {
        return chatModel(settings).call(buildPrompt(systemPrompt, userMessage, settings));
    }

    @Override
    public Flux<String> stream(String systemPrompt, String userMessage, ChatSettings settings) {
        return chatModel(settings).stream(buildPrompt(systemPrompt, userMessage, settings))
            .mapNotNull(response -> response.getResult() != null && response.getResult().getOutput() != null
                ? response.getResult().getOutput().getText() : null)
            .filter(text -> !text.isEmpty());
    }

    @Override
    public List<Double> embed(String text, EmbedSettings settings) {
        throw new LlmNotConfiguredException(
            "Anthropic offers no embedding API — set this tenant's embedding provider to 'openai' "
                + "(with its own key) in the admin AI settings");
    }

    @Override
    public boolean supportsEmbeddings() {
        return false;
    }

    private Prompt buildPrompt(String systemPrompt, String userMessage, ChatSettings settings) {
        List<org.springframework.ai.chat.messages.Message> messages = (systemPrompt == null || systemPrompt.isBlank())
            ? List.of(new UserMessage(userMessage))
            : List.of(new SystemMessage(systemPrompt), new UserMessage(userMessage));
        return new Prompt(messages, AnthropicChatOptions.builder()
            .model(settings.model())
            .temperature(settings.temperature())
            .maxTokens(settings.maxTokens())
            .build());
    }

    /** Builds a one-off client bound to the tenant's key/endpoint. Not cached — see
     * {@link OpenAILLMProvider}'s equivalent for the same rationale. */
    private AnthropicChatModel chatModel(ChatSettings settings) {
        String baseUrl = settings.baseUrl() != null && !settings.baseUrl().isBlank()
            ? settings.baseUrl() : DEFAULT_BASE_URL;
        AnthropicApi api = AnthropicApi.builder().baseUrl(baseUrl).apiKey(settings.apiKey()).build();
        return AnthropicChatModel.builder().anthropicApi(api).build();
    }
}

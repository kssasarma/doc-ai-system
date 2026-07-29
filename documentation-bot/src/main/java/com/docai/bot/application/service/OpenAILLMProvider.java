package com.docai.bot.application.service;

import java.util.List;

import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.OpenAiEmbeddingModel;
import org.springframework.ai.openai.OpenAiEmbeddingOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.stereotype.Component;

import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;

/**
 * OpenAI (and OpenAI-compatible endpoints, via each tenant's base-URL setting). Clients are built
 * per call from the tenant's own settings — there is no Spring-AI-autoconfigured platform client
 * anymore, and the service boots without any OPENAI_API_KEY.
 */
@Slf4j
@Component
public class OpenAILLMProvider implements LLMProvider {

    static final String DEFAULT_BASE_URL = "https://api.openai.com";

    @Override
    public String providerName() {
        return "openai";
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
        var request = new EmbeddingRequest(List.of(text),
            OpenAiEmbeddingOptions.builder().model(settings.model()).build());
        var response = embeddingModel(settings).call(request);
        float[] raw = response.getResult().getOutput();
        List<Double> result = new java.util.ArrayList<>(raw.length);
        for (float v : raw) result.add((double) v);
        return result;
    }

    private Prompt buildPrompt(String systemPrompt, String userMessage, ChatSettings settings) {
        List<org.springframework.ai.chat.messages.Message> messages = (systemPrompt == null || systemPrompt.isBlank())
            ? List.of(new UserMessage(userMessage))
            : List.of(new SystemMessage(systemPrompt), new UserMessage(userMessage));
        return new Prompt(messages, OpenAiChatOptions.builder()
            .model(settings.model())
            .temperature(settings.temperature())
            .maxTokens(settings.maxTokens())
            .build());
    }

    /** Builds a one-off client bound to the tenant's key/endpoint. Not cached — building one is
     * cheap (no network call), and always-fresh avoids any stale-key-after-rotation risk. */
    private OpenAiChatModel chatModel(ChatSettings settings) {
        OpenAiApi api = OpenAiApi.builder()
            .baseUrl(baseUrlOrDefault(settings.baseUrl()))
            .apiKey(settings.apiKey())
            .build();
        return OpenAiChatModel.builder().openAiApi(api).build();
    }

    private OpenAiEmbeddingModel embeddingModel(EmbedSettings settings) {
        OpenAiApi api = OpenAiApi.builder()
            .baseUrl(baseUrlOrDefault(settings.baseUrl()))
            .apiKey(settings.apiKey())
            .build();
        return new OpenAiEmbeddingModel(api);
    }

    private static String baseUrlOrDefault(String baseUrl) {
        return baseUrl != null && !baseUrl.isBlank() ? baseUrl : DEFAULT_BASE_URL;
    }
}

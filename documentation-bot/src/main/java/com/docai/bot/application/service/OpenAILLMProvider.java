package com.docai.bot.application.service;

import java.util.List;

import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.OpenAiEmbeddingModel;
import org.springframework.ai.openai.OpenAiEmbeddingOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;

@Slf4j
@Component
public class OpenAILLMProvider implements LLMProvider {

    private final OpenAiChatModel chatModel;
    private final EmbeddingModel embeddingModel;

    @Value("${spring.ai.openai.base-url}")
    private String baseUrl;

    public OpenAILLMProvider(OpenAiChatModel chatModel, EmbeddingModel embeddingModel) {
        this.chatModel      = chatModel;
        this.embeddingModel = embeddingModel;
    }

    @Override
    public String providerName() {
        return "openai";
    }

    @Override
    public ChatResponse chat(String systemPrompt, String userMessage, String model, String apiKeyOverride) {
        Prompt prompt = buildPrompt(systemPrompt, userMessage, model);
        OpenAiChatModel client = apiKeyOverride != null ? tenantChatModel(apiKeyOverride) : chatModel;
        return client.call(prompt);
    }

    @Override
    public Flux<String> stream(String systemPrompt, String userMessage, String model, String apiKeyOverride) {
        Prompt prompt = buildPrompt(systemPrompt, userMessage, model);
        OpenAiChatModel client = apiKeyOverride != null ? tenantChatModel(apiKeyOverride) : chatModel;
        return client.stream(prompt)
            .mapNotNull(response -> response.getResult() != null && response.getResult().getOutput() != null
                ? response.getResult().getOutput().getText() : null)
            .filter(text -> !text.isEmpty());
    }

    @Override
    public List<Double> embed(String text, String model, String apiKeyOverride) {
        var request = new EmbeddingRequest(List.of(text),
            OpenAiEmbeddingOptions.builder().model(model).build());
        var response = apiKeyOverride != null
            ? tenantEmbeddingModel(apiKeyOverride).call(request)
            : embeddingModel.call(request);
        float[] raw = response.getResult().getOutput();
        List<Double> result = new java.util.ArrayList<>(raw.length);
        for (float v : raw) result.add((double) v);
        return result;
    }

    private Prompt buildPrompt(String systemPrompt, String userMessage, String model) {
        List<org.springframework.ai.chat.messages.Message> messages = (systemPrompt == null || systemPrompt.isBlank())
            ? List.of(new UserMessage(userMessage))
            : List.of(new SystemMessage(systemPrompt), new UserMessage(userMessage));
        return new Prompt(messages, OpenAiChatOptions.builder().model(model).build());
    }

    /** Builds a one-off client bound to a tenant's own (decrypted) API key. Not cached — building
     * one is cheap (no network call), and always-fresh avoids any stale-key-after-rotation risk. */
    private OpenAiChatModel tenantChatModel(String apiKey) {
        OpenAiApi api = OpenAiApi.builder().baseUrl(baseUrl).apiKey(apiKey).build();
        return OpenAiChatModel.builder().openAiApi(api).build();
    }

    private OpenAiEmbeddingModel tenantEmbeddingModel(String apiKey) {
        OpenAiApi api = OpenAiApi.builder().baseUrl(baseUrl).apiKey(apiKey).build();
        return new OpenAiEmbeddingModel(api);
    }
}

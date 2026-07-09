package com.docai.bot.application.service;

import java.util.List;

import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.OpenAiEmbeddingOptions;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingRequest;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
public class OpenAILLMProvider implements LLMProvider {

    private final OpenAiChatModel chatModel;
    private final EmbeddingModel embeddingModel;

    @Value("${spring.ai.openai.api-key}")
    private String apiKey;

    public OpenAILLMProvider(OpenAiChatModel chatModel, EmbeddingModel embeddingModel) {
        this.chatModel      = chatModel;
        this.embeddingModel = embeddingModel;
    }

    @Override
    public String providerName() {
        return "openai";
    }

    @Override
    public String chat(String systemPrompt, String userMessage, String model) {
        return chatModel.call(new Prompt(
            List.of(new SystemMessage(systemPrompt), new UserMessage(userMessage)),
            OpenAiChatOptions.builder().model(model).build()
        )).getResult().getOutput().getText();
    }

    @Override
    public List<Double> embed(String text, String model) {
        var response = embeddingModel.call(
            new EmbeddingRequest(List.of(text),
                OpenAiEmbeddingOptions.builder().model(model).build()));
        float[] raw = response.getResult().getOutput();
        List<Double> result = new java.util.ArrayList<>(raw.length);
        for (float v : raw) result.add((double) v);
        return result;
    }
}

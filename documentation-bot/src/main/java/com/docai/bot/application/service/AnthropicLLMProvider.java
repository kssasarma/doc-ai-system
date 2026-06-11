package com.docai.bot.application.service;

import java.util.List;

import org.springframework.ai.anthropic.AnthropicChatModel;
import org.springframework.ai.anthropic.AnthropicChatOptions;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

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

    @Override
    public String providerName() {
        return "anthropic";
    }

    @Override
    public String chat(String systemPrompt, String userMessage, String model) {
        return anthropicChatModel.call(new Prompt(
            List.of(new SystemMessage(systemPrompt), new UserMessage(userMessage)),
            AnthropicChatOptions.builder().model(model).build()
        )).getResult().getOutput().getText();
    }

    @Override
    public List<Double> embed(String text, String model) {
        // Anthropic has no public embedding API — delegate to OpenAI
        log.debug("Anthropic embed delegating to OpenAI for embedding");
        return openAIProvider.embed(text, model);
    }
}

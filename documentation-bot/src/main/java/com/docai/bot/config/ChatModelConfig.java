package com.docai.bot.config;

import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

/**
 * Both the OpenAI and Anthropic starters are on the classpath (see LLMRouter), so Spring AI's
 * autoconfigured ChatClient.Builder can't pick a single ChatModel bean on its own. OpenAI is the
 * global default provider, so mark it @Primary for that autoconfiguration to resolve.
 */
@Configuration
public class ChatModelConfig {

    @Bean
    @Primary
    public ChatModel primaryChatModel(OpenAiChatModel openAiChatModel) {
        return openAiChatModel;
    }
}

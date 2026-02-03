package com.docai.bot.application.service;

import java.util.List;
import java.util.UUID;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.docai.bot.domain.entity.ChatMessage;
import com.docai.bot.domain.entity.ChatSummary;
import com.docai.bot.domain.repository.ChatMessageRepository;
import com.docai.bot.domain.repository.ChatSummaryRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class ChatSummaryService {

    private final ChatMessageRepository messageRepository;
    private final ChatSummaryRepository summaryRepository;
    private final ChatClient.Builder chatClientBuilder;

    @Value("${bot.summary-threshold:15}")
    private int summaryThreshold;

    @Async
    @Transactional
    public void summarizeIfNeeded(UUID chatId) {
        long messageCount = messageRepository.countByChatId(chatId);
        
        if (messageCount >= summaryThreshold) {
            log.info("Summarizing chat {} with {} messages", chatId, messageCount);
            summarizeChat(chatId);
        }
    }

    @Transactional
    public void summarizeChat(UUID chatId) {
        try {
            // Get all messages
            List<ChatMessage> messages = messageRepository
                .findByChatIdOrderByCreatedAtDesc(chatId);
            
            if (messages.isEmpty()) {
                return;
            }
            
            // Build conversation text
            StringBuilder conversation = new StringBuilder();
            for (int i = messages.size() - 1; i >= 0; i--) {
                ChatMessage msg = messages.get(i);
                conversation.append(msg.getRole()).append(": ");
                conversation.append(msg.getContent()).append("\n");
            }
            
            // Generate summary using LLM
            String summaryPrompt = "Summarize the following conversation concisely, " +
                "focusing on the key topics discussed and main questions asked:\n\n" +
                conversation.toString();
            
            ChatClient chatClient = chatClientBuilder.build();
            String summary = chatClient.prompt()
                .user(summaryPrompt)
                .call()
                .content();
            
            // Save or update summary
            ChatSummary chatSummary = summaryRepository.findById(chatId)
                .orElse(ChatSummary.builder().chatId(chatId).build());
            
            chatSummary.setSummary(summary);
            summaryRepository.save(chatSummary);
            
            log.info("Successfully summarized chat {}", chatId);
            
        } catch (Exception e) {
            log.error("Failed to summarize chat {}", chatId, e);
        }
    }
}

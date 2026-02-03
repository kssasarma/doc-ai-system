package com.docai.bot.application.service;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.docai.bot.domain.entity.ChatMessage;
import com.docai.bot.domain.entity.ChatSummary;
import com.docai.bot.domain.repository.ChatMessageRepository;
import com.docai.bot.domain.repository.ChatSummaryRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class ContextManager {

    private final ChatMessageRepository messageRepository;
    private final ChatSummaryRepository summaryRepository;

    @Value("${bot.max-context-messages:10}")
    private int maxContextMessages;

    public String buildContextPrompt(UUID chatId) {
        StringBuilder context = new StringBuilder();
        
        // Try to get summary first
        Optional<ChatSummary> summary = summaryRepository.findById(chatId);
        if (summary.isPresent()) {
            context.append("Previous conversation summary:\n");
            context.append(summary.get().getSummary());
            context.append("\n\n");
        }
        
        // Get recent messages
        List<ChatMessage> recentMessages = messageRepository
            .findRecentMessages(chatId, maxContextMessages);
        
        if (!recentMessages.isEmpty()) {
            context.append("Recent conversation:\n");
            // Reverse to get chronological order
            for (int i = recentMessages.size() - 1; i >= 0; i--) {
                ChatMessage msg = recentMessages.get(i);
                context.append(msg.getRole()).append(": ");
                context.append(msg.getContent()).append("\n");
            }
        }
        
        return context.toString();
    }

    public int getContextMessageCount(UUID chatId) {
        return (int) messageRepository.countByChatId(chatId);
    }

    public String getConversationHistory(UUID chatId, int limit) {
        List<ChatMessage> messages = messageRepository.findRecentMessages(chatId, limit);
        
        return messages.stream()
            .sorted((m1, m2) -> m1.getCreatedAt().compareTo(m2.getCreatedAt()))
            .map(m -> m.getRole() + ": " + m.getContent())
            .collect(Collectors.joining("\n"));
    }
}

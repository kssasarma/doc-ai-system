package com.docai.bot.application.service;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.docai.bot.domain.entity.ChatMessage;
import com.docai.bot.domain.entity.ChatSession;
import com.docai.bot.domain.model.RetrievedChunk;
import com.docai.bot.domain.repository.ChatMessageRepository;
import com.docai.bot.domain.repository.ChatSessionRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class ChatService {

    private final ChatSessionRepository sessionRepository;
    private final ChatMessageRepository messageRepository;
    private final VectorSearchService vectorSearchService;
    private final ContextManager contextManager;
    private final ChatSummaryService summaryService;
    private final AnswerGenerationService answerService;

    @Transactional
    public ChatResponse processQuery(ChatRequest request) {
        log.info("Processing query for product: {} version: {}", 
                 request.getProduct(), request.getVersion());
        
        // Get or create chat session
        ChatSession session = getOrCreateSession(
            request.getChatId(), request.getProduct(), request.getVersion());
        
        // Build context from chat history
        String chatContext = contextManager.buildContextPrompt(session.getId());
        
        // Enhance query with context if available
        String enhancedQuery = enhanceQueryWithContext(
            request.getQuestion(), chatContext);
        
        // Retrieve relevant chunks using vector search
        List<RetrievedChunk> relevantChunks = vectorSearchService.search(
            enhancedQuery, request.getProduct(), request.getVersion());
        
        // Generate answer
        String answer = answerService.generateAnswer(
            request.getQuestion(), chatContext, relevantChunks);
        
        // Save messages
        saveMessage(session.getId(), ChatMessage.Role.USER, request.getQuestion());
        saveMessage(session.getId(), ChatMessage.Role.ASSISTANT, answer);
        
        // Update session
        session.setMessageCount(session.getMessageCount() + 2);
        sessionRepository.save(session);
        
        // Trigger summarization if needed (async)
        summaryService.summarizeIfNeeded(session.getId());
        
        // Build response
        List<SourceReference> sources = relevantChunks.stream()
            .map(chunk -> SourceReference.builder()
                .document(chunk.getDocumentName())
                .chunkId(chunk.getChunkId())
                .build())
            .collect(Collectors.toList());
        
        return ChatResponse.builder()
            .chatId(session.getId().toString())
            .answer(answer)
            .sources(sources)
            .build();
    }

    private ChatSession getOrCreateSession(String chatIdStr, String product, String version) {
        if (chatIdStr != null && !chatIdStr.isEmpty()) {
            try {
                UUID chatId = UUID.fromString(chatIdStr);
                return sessionRepository.findById(chatId)
                    .orElseGet(() -> createNewSession(product, version));
            } catch (IllegalArgumentException e) {
                log.warn("Invalid chatId format: {}", chatIdStr);
            }
        }
        return createNewSession(product, version);
    }

    private ChatSession createNewSession(String product, String version) {
        ChatSession session = ChatSession.builder()
            .product(product)
            .version(version)
            .messageCount(0)
            .build();
        return sessionRepository.save(session);
    }

    private void saveMessage(UUID chatId, ChatMessage.Role role, String content) {
        ChatMessage message = ChatMessage.builder()
            .chatId(chatId)
            .role(role)
            .content(content)
            .build();
        messageRepository.save(message);
    }

    private String enhanceQueryWithContext(String question, String context) {
        if (context == null || context.isEmpty()) {
            return question;
        }
        
        // For follow-up questions, add context hint
        return question + "\n\n(Context from previous conversation:\n" + 
               context.substring(0, Math.min(500, context.length())) + ")";
    }

    // DTOs
    @lombok.Data
    @lombok.Builder
    public static class ChatRequest {
        private String chatId;
        private String product;
        private String version;
        private String question;
    }

    @lombok.Data
    @lombok.Builder
    public static class ChatResponse {
        private String chatId;
        private String answer;
        private List<SourceReference> sources;
    }

    @lombok.Data
    @lombok.Builder
    public static class SourceReference {
        private String document;
        private String chunkId;
    }
}

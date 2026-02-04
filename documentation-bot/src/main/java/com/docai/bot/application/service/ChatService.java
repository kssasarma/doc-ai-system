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
import com.docai.bot.domain.repository.ChatSummaryRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class ChatService {

    private final ChatSessionRepository sessionRepository;
    private final ChatMessageRepository messageRepository;
    private final ChatSummaryRepository summaryRepository;
    private final VectorSearchService vectorSearchService;
    private final ContextManager contextManager;
    private final ChatSummaryService summaryService;
    private final AnswerGenerationService answerService;
    private final QueryAnalyzerService queryAnalyzer;

    @Transactional
    public ChatResponse processQuery(ChatRequest request) {
        log.info("Processing query: {}", request.getQuestion());
        
        // Get or create chat session first
        ChatSession session = getOrCreateSession(
            request.getChatId(), request.getProduct(), request.getVersion());
        
        // Analyze query to extract product and version
        QueryAnalyzerService.QueryContext queryContext = queryAnalyzer.analyzeQuery(
            request.getQuestion(), request.getChatId());
        
        // Priority order: AI detected > Request params > Session stored values
        String product = queryContext.getProduct() != null ? 
            queryContext.getProduct() : 
            (request.getProduct() != null ? request.getProduct() : session.getProduct());
        String version = queryContext.getVersion() != null ? 
            queryContext.getVersion() : 
            (request.getVersion() != null ? request.getVersion() : session.getVersion());
        
        log.info("Using - Product: {}, Version: {}", product, version);
        
        // Build context from chat history
        String chatContext = contextManager.buildContextPrompt(session.getId());
        
        // Enhance query with context if available
        String enhancedQuery = enhanceQueryWithContext(
            request.getQuestion(), chatContext);
        
        // Retrieve relevant chunks using vector search
        List<RetrievedChunk> relevantChunks = vectorSearchService.search(
            enhancedQuery, product, version);
        
        // Generate answer
        String answer = answerService.generateAnswer(
            request.getQuestion(), chatContext, relevantChunks);
        
        // Save messages
        saveMessage(session.getId(), ChatMessage.Role.USER, request.getQuestion());
        saveMessage(session.getId(), ChatMessage.Role.ASSISTANT, answer);
        
        // Update session with detected product/version if not set
        if (session.getProduct() == null && product != null) {
            session.setProduct(product);
        }
        if (session.getVersion() == null && version != null) {
            session.setVersion(version);
        }
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

    @Transactional(readOnly = true)
    public ChatHistoryResponse getChatHistory(String chatIdStr) {
        log.info("Retrieving chat history for chatId: {}", chatIdStr);
        
        try {
            UUID chatId = UUID.fromString(chatIdStr);
            
            // Get all messages ordered by creation time (oldest first for chronological order)
            List<ChatMessage> messages = messageRepository.findByChatIdOrderByCreatedAtDesc(chatId);
            
            // Reverse to get chronological order (oldest to newest)
            java.util.Collections.reverse(messages);
            
            // Convert to DTOs
            List<ChatMessageDTO> messageDTOs = messages.stream()
                .map(msg -> ChatMessageDTO.builder()
                    .id(msg.getId().toString())
                    .role(msg.getRole().toString())
                    .content(msg.getContent())
                    .createdAt(msg.getCreatedAt())
                    .build())
                .collect(Collectors.toList());
            
            return ChatHistoryResponse.builder()
                .chatId(chatId.toString())
                .messageCount(messageDTOs.size())
                .messages(messageDTOs)
                .build();
                
        } catch (IllegalArgumentException e) {
            log.error("Invalid chatId format: {}", chatIdStr, e);
            throw new IllegalArgumentException("Invalid chat ID format", e);
        }
    }

    @lombok.Data
    @lombok.Builder
    public static class ChatHistoryResponse {
        private String chatId;
        private int messageCount;
        private List<ChatMessageDTO> messages;
    }

    @lombok.Data
    @lombok.Builder
    public static class ChatMessageDTO {
        private String id;
        private String role;
        private String content;
        private java.time.LocalDateTime createdAt;
    }

    @Transactional(readOnly = true)
    public AllChatsResponse getAllChatSessions() {
        log.info("Retrieving all chat sessions");
        
        // Get all chat sessions ordered by last activity (most recent first)
        List<ChatSession> sessions = sessionRepository.findAll(
            org.springframework.data.domain.Sort.by(
                org.springframework.data.domain.Sort.Direction.DESC, "lastActiveAt"
            )
        );
        
        // Convert to DTOs
        List<ChatSessionDTO> sessionDTOs = sessions.stream()
            .map(session -> ChatSessionDTO.builder()
                .chatId(session.getId().toString())
                .product(session.getProduct())
                .version(session.getVersion())
                .messageCount(session.getMessageCount())
                .createdAt(session.getCreatedAt())
                .lastActiveAt(session.getLastActiveAt())
                .build())
            .collect(Collectors.toList());
        
        return AllChatsResponse.builder()
            .totalChats(sessionDTOs.size())
            .sessions(sessionDTOs)
            .build();
    }

    @lombok.Data
    @lombok.Builder
    public static class AllChatsResponse {
        private int totalChats;
        private List<ChatSessionDTO> sessions;
    }

    @lombok.Data
    @lombok.Builder
    public static class ChatSessionDTO {
        private String chatId;
        private String product;
        private String version;
        private Integer messageCount;
        private java.time.LocalDateTime createdAt;
        private java.time.LocalDateTime lastActiveAt;
    }

    @Transactional
    public void deleteChatSession(String chatIdStr) {
        log.info("Deleting chat session: {}", chatIdStr);
        
        try {
            UUID chatId = UUID.fromString(chatIdStr);
            
            // Delete related entities in order: messages, summary, then session
            messageRepository.deleteByChatId(chatId);
            log.debug("Deleted messages for chatId: {}", chatId);
            
            summaryRepository.deleteById(chatId);
            log.debug("Deleted summary for chatId: {}", chatId);
            
            sessionRepository.deleteById(chatId);
            log.info("Successfully deleted chat session: {}", chatId);
            
        } catch (IllegalArgumentException e) {
            log.error("Invalid chatId format: {}", chatIdStr, e);
            throw new IllegalArgumentException("Invalid chat ID format", e);
        }
    }
}

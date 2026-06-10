package com.docai.bot.application.service;

import java.time.LocalDateTime;
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

        ChatSession session = getOrCreateSession(request.getChatId(), request.getProduct(),
                request.getVersion(), request.getUserId());

        QueryAnalyzerService.QueryContext queryContext =
                queryAnalyzer.analyzeQuery(request.getQuestion(), request.getChatId());

        String product = queryContext.getProduct() != null ? queryContext.getProduct()
                : (request.getProduct() != null ? request.getProduct() : session.getProduct());
        String version = queryContext.getVersion() != null ? queryContext.getVersion()
                : (request.getVersion() != null ? request.getVersion() : session.getVersion());

        log.info("Using product: {}, version: {}", product, version);

        String chatContext = contextManager.buildContextPrompt(session.getId());
        String enhancedQuery = enhanceQueryWithContext(request.getQuestion(), chatContext);

        List<RetrievedChunk> relevantChunks = vectorSearchService.search(enhancedQuery, product, version);
        String answer = answerService.generateAnswer(request.getQuestion(), chatContext, relevantChunks);

        saveMessage(session.getId(), ChatMessage.Role.USER, request.getQuestion());
        saveMessage(session.getId(), ChatMessage.Role.ASSISTANT, answer);

        if (session.getProduct() == null && product != null) {
            session.setProduct(product);
        }
        if (session.getVersion() == null && version != null) {
            session.setVersion(version);
        }
        session.setMessageCount(session.getMessageCount() + 2);
        sessionRepository.save(session);

        summaryService.summarizeIfNeeded(session.getId());

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

    private ChatSession getOrCreateSession(String chatIdStr, String product, String version, UUID userId) {
        if (chatIdStr != null && !chatIdStr.isEmpty()) {
            try {
                UUID chatId = UUID.fromString(chatIdStr);
                return sessionRepository.findById(chatId)
                    .orElseGet(() -> createNewSession(product, version, userId));
            } catch (IllegalArgumentException e) {
                log.warn("Invalid chatId format: {}", chatIdStr);
            }
        }
        return createNewSession(product, version, userId);
    }

    private ChatSession createNewSession(String product, String version, UUID userId) {
        ChatSession session = ChatSession.builder()
            .userId(userId)
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
        return question + "\n\n(Context from previous conversation:\n" +
               context.substring(0, Math.min(500, context.length())) + ")";
    }

    @Transactional(readOnly = true)
    public ChatHistoryResponse getChatHistory(String chatIdStr) {
        log.info("Retrieving chat history for chatId: {}", chatIdStr);

        try {
            UUID chatId = UUID.fromString(chatIdStr);
            List<ChatMessage> messages = messageRepository.findByChatIdOrderByCreatedAtAsc(chatId);

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
            throw new IllegalArgumentException("Invalid chat ID format", e);
        }
    }

    @Transactional(readOnly = true)
    public AllChatsResponse getAllChatSessions(UUID userId, boolean isAdmin) {
        log.info("Retrieving chat sessions for userId: {}, isAdmin: {}", userId, isAdmin);

        List<ChatSession> sessions = isAdmin
            ? sessionRepository.findAllByOrderByLastActiveAtDesc()
            : sessionRepository.findByUserIdOrderByLastActiveAtDesc(userId);

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

    @Transactional
    public void deleteChatSession(String chatIdStr) {
        log.info("Deleting chat session: {}", chatIdStr);
        try {
            UUID chatId = UUID.fromString(chatIdStr);
            messageRepository.deleteByChatId(chatId);
            summaryRepository.deleteById(chatId);
            sessionRepository.deleteById(chatId);
            log.info("Deleted chat session: {}", chatId);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Invalid chat ID format", e);
        }
    }

    // DTOs
    @lombok.Data
    @lombok.Builder
    public static class ChatRequest {
        private String chatId;
        private String product;
        private String version;
        private String question;
        private UUID userId;
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
        private LocalDateTime createdAt;
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
        private LocalDateTime createdAt;
        private LocalDateTime lastActiveAt;
    }
}

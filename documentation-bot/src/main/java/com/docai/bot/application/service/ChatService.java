package com.docai.bot.application.service;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.docai.bot.config.TenantContext;
import com.docai.bot.domain.entity.ChatMessage;
import com.docai.bot.domain.entity.ChatSession;
import com.docai.bot.domain.entity.UserPreference;
import com.docai.bot.domain.model.RetrievedChunk;
import com.docai.bot.domain.model.SearchScope;
import com.docai.bot.domain.repository.AnswerUpvoteRepository;
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
    private final AnswerUpvoteRepository upvoteRepository;
    private final VectorSearchService vectorSearchService;
    private final DocumentAccessPolicy documentAccessPolicy;
    private final ContextManager contextManager;
    private final ChatSummaryService summaryService;
    private final AnswerGenerationService answerService;
    private final MultiHopReasoningService multiHopService;
    private final PeopleAlsoAskedService peopleAlsoAskedService;
    private final QueryAnalyzerService queryAnalyzer;
    private final UserPreferenceService preferenceService;
    private final AnalyticsService analyticsService;

    @Value("${bot.cost.input-per-1k-tokens:0.00015}")
    private double costPerKInput;

    @Value("${bot.cost.output-per-1k-tokens:0.00060}")
    private double costPerKOutput;

    @Transactional
    public ChatResponse processQuery(ChatRequest request) {
        log.info("Processing query: {}", request.getQuestion());
        long startMs = System.currentTimeMillis();

        ChatSession session = getOrCreateSession(request.getChatId(), request.getProduct(),
                request.getVersion(), request.getUserId());

        boolean isFirstExchange = session.getMessageCount() == 0;

        QueryAnalyzerService.QueryContext queryContext =
                queryAnalyzer.analyzeQuery(request.getQuestion(), request.getChatId());

        String product = queryContext.getProduct() != null ? queryContext.getProduct()
                : (request.getProduct() != null ? request.getProduct() : session.getProduct());
        String version = queryContext.getVersion() != null ? queryContext.getVersion()
                : (request.getVersion() != null ? request.getVersion() : session.getVersion());

        log.info("Using product: {}, version: {}", product, version);

        SearchScope scope = documentAccessPolicy.resolveScope(request.getUserId(), TenantContext.get());

        UserPreference prefs = preferenceService.getPreferences(request.getUserId());
        String chatContext = contextManager.buildContextPrompt(session.getId());
        String enhancedQuery = enhanceQueryWithContext(request.getQuestion(), chatContext);

        String finalAnswer;
        List<String> relatedQuestionsFromLlm;
        List<RetrievedChunk> relevantChunks;
        List<MultiHopReasoningService.HopResult> reasoningChain = null;
        int promptTokens, completionTokens;

        if (multiHopService.isComplexQuery(request.getQuestion())) {
            log.info("Complex query detected — using multi-hop reasoning");
            MultiHopReasoningService.MultiHopAnswer multiHop = multiHopService.reason(
                request.getQuestion(), chatContext, scope, product, version,
                prefs.getVerbosity(), prefs.getAnswerFormat()
            );
            finalAnswer = multiHop.answer();
            relatedQuestionsFromLlm = multiHop.relatedQuestions();
            reasoningChain = multiHop.hops();
            relevantChunks = multiHop.hops().stream()
                .flatMap(h -> h.chunks().stream()).distinct().toList();
            promptTokens = multiHop.promptTokens();
            completionTokens = multiHop.completionTokens();
        } else {
            relevantChunks = vectorSearchService.search(enhancedQuery, scope);
            AnswerGenerationService.AnswerResult result = answerService.generateAnswer(
                request.getQuestion(), chatContext, relevantChunks,
                prefs.getVerbosity(), prefs.getAnswerFormat()
            );
            finalAnswer = result.answer();
            relatedQuestionsFromLlm = result.relatedQuestions();
            promptTokens = result.promptTokens();
            completionTokens = result.completionTokens();
        }

        saveMessage(session.getId(), ChatMessage.Role.USER, request.getQuestion());
        ChatMessage assistantMessage = saveMessage(session.getId(), ChatMessage.Role.ASSISTANT, finalAnswer);

        if (session.getProduct() == null && product != null) session.setProduct(product);
        if (session.getVersion() == null && version != null) session.setVersion(version);
        session.setMessageCount(session.getMessageCount() + 2);

        String sessionTitle = null;
        if (isFirstExchange) {
            sessionTitle = answerService.generateSessionTitle(request.getQuestion(), finalAnswer);
            session.setTitle(sessionTitle);
            log.info("Generated session title: {}", sessionTitle);
        }

        sessionRepository.save(session);
        summaryService.summarizeIfNeeded(session.getId());

        double confidence = calculateConfidence(relevantChunks, version);
        List<SourceReference> sources = buildSources(relevantChunks);

        // Async: log query to session graph + analytics
        long latencyMs = System.currentTimeMillis() - startMs;
        String[] citedDocs = relevantChunks.stream()
            .map(RetrievedChunk::getDocumentName)
            .distinct()
            .toArray(String[]::new);
        double estimatedCost = (promptTokens / 1000.0 * costPerKInput)
            + (completionTokens / 1000.0 * costPerKOutput);
        analyticsService.logQuery(
            request.getUserId(), session.getId(), request.getQuestion(),
            product, version, confidence, latencyMs,
            promptTokens, completionTokens, citedDocs, estimatedCost
        );
        peopleAlsoAskedService.recordQuery(
            session.getId(), request.getUserId(), request.getQuestion(), product, version
        );

        // "People Also Asked" from real query data; fall back to LLM-generated if sparse
        List<String> realAlsoAsked = peopleAlsoAskedService.getPeopleAlsoAsked(
            request.getQuestion(), session.getId(), product, version
        );
        List<String> relatedQuestions = realAlsoAsked.isEmpty() ? relatedQuestionsFromLlm : realAlsoAsked;

        // Build reasoning chain summary for multi-hop responses
        List<ReasoningStep> chain = null;
        if (reasoningChain != null) {
            chain = reasoningChain.stream()
                .map(h -> ReasoningStep.builder()
                    .subQuestion(h.subQuestion())
                    .chunksFound(h.chunks().size())
                    .maxSimilarity(h.maxSimilarity())
                    .build())
                .toList();
        }

        return ChatResponse.builder()
            .chatId(session.getId().toString())
            .messageId(assistantMessage.getId().toString())
            .answer(finalAnswer)
            .sources(sources)
            .confidence(confidence)
            .sessionTitle(sessionTitle)
            .relatedQuestions(relatedQuestions)
            .reasoningChain(chain)
            .build();
    }

    @Transactional
    public ChatResponse regenerateAnswer(String messageIdStr, UUID userId, String style) {
        UUID messageId = UUID.fromString(messageIdStr);
        ChatMessage assistantMsg = messageRepository.findById(messageId)
            .orElseThrow(() -> new IllegalArgumentException("Message not found"));

        // Find the preceding user message
        List<ChatMessage> messages = messageRepository.findByChatIdOrderByCreatedAtAsc(assistantMsg.getChatId());
        String question = null;
        for (int i = 0; i < messages.size(); i++) {
            if (messages.get(i).getId().equals(messageId) && i > 0) {
                question = messages.get(i - 1).getContent();
                break;
            }
        }
        if (question == null) throw new IllegalArgumentException("Original question not found");

        ChatSession session = sessionRepository.findById(assistantMsg.getChatId())
            .orElseThrow(() -> new IllegalArgumentException("Session not found"));

        String verbosity = switch (style) {
            case "CONCISE" -> "CONCISE";
            case "DETAILED" -> "DETAILED";
            default -> "BALANCED";
        };
        String format = "CODE_FIRST".equals(style) ? "CODE_FIRST" : "PROSE";

        SearchScope scope = documentAccessPolicy.resolveScope(userId, TenantContext.get());
        List<RetrievedChunk> chunks = vectorSearchService.search(question, scope);
        AnswerGenerationService.AnswerResult result = answerService.generateAnswer(
            question, null, chunks, verbosity, format
        );

        double confidence = calculateConfidence(chunks, session.getVersion());
        return ChatResponse.builder()
            .chatId(session.getId().toString())
            .messageId(messageIdStr)
            .answer(result.answer())
            .sources(buildSources(chunks))
            .confidence(confidence)
            .relatedQuestions(result.relatedQuestions())
            .build();
    }

    @Transactional
    public SessionUpdateResponse updateSession(String chatIdStr, UUID userId,
                                               String title, Boolean pinned, String[] tags) {
        UUID chatId = UUID.fromString(chatIdStr);
        ChatSession session = sessionRepository.findById(chatId)
            .orElseThrow(() -> new IllegalArgumentException("Session not found"));

        if (title != null) session.setTitle(title.isBlank() ? null : title);
        if (pinned != null) session.setPinned(pinned);
        if (tags != null) session.setTags(tags);

        ChatSession saved = sessionRepository.save(session);
        log.info("Updated session {} for user {}", chatId, userId);

        return SessionUpdateResponse.builder()
            .chatId(saved.getId().toString())
            .title(saved.getTitle())
            .pinned(saved.isPinned())
            .tags(saved.getTags())
            .build();
    }

    @Transactional(readOnly = true)
    public String exportSession(String chatIdStr, String format) {
        UUID chatId = UUID.fromString(chatIdStr);
        ChatSession session = sessionRepository.findById(chatId)
            .orElseThrow(() -> new IllegalArgumentException("Session not found"));
        List<ChatMessage> messages = messageRepository.findByChatIdOrderByCreatedAtAsc(chatId);

        if ("json".equalsIgnoreCase(format)) {
            return buildJsonExport(session, messages);
        }
        return buildMarkdownExport(session, messages);
    }

    private String buildMarkdownExport(ChatSession session, List<ChatMessage> messages) {
        StringBuilder md = new StringBuilder();
        String title = session.getTitle() != null ? session.getTitle()
            : (session.getProduct() != null ? session.getProduct() + " " + session.getVersion() : "Chat Session");
        md.append("# ").append(title).append("\n\n");
        if (session.getProduct() != null) {
            md.append("**Product:** ").append(session.getProduct());
            if (session.getVersion() != null) md.append(" ").append(session.getVersion());
            md.append("  \n");
        }
        md.append("**Messages:** ").append(messages.size()).append("  \n");
        md.append("**Created:** ").append(session.getCreatedAt()).append("\n\n---\n\n");

        for (ChatMessage msg : messages) {
            String role = msg.getRole() == ChatMessage.Role.USER ? "**You**" : "**Assistant**";
            md.append(role).append("\n\n").append(msg.getContent()).append("\n\n---\n\n");
        }
        return md.toString();
    }

    private String buildJsonExport(ChatSession session, List<ChatMessage> messages) {
        StringBuilder json = new StringBuilder();
        json.append("{\"chatId\":\"").append(session.getId()).append("\"");
        json.append(",\"title\":").append(jsonStr(session.getTitle()));
        json.append(",\"product\":").append(jsonStr(session.getProduct()));
        json.append(",\"version\":").append(jsonStr(session.getVersion()));
        json.append(",\"createdAt\":\"").append(session.getCreatedAt()).append("\"");
        json.append(",\"messages\":[");
        for (int i = 0; i < messages.size(); i++) {
            ChatMessage m = messages.get(i);
            if (i > 0) json.append(",");
            json.append("{\"role\":\"").append(m.getRole().toString().toLowerCase()).append("\"");
            json.append(",\"content\":").append(jsonStr(m.getContent()));
            json.append(",\"timestamp\":\"").append(m.getCreatedAt()).append("\"}");
        }
        json.append("]}");
        return json.toString();
    }

    private static String jsonStr(String s) {
        if (s == null) return "null";
        return "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"")
            .replace("\n", "\\n").replace("\r", "\\r").replace("\t", "\\t") + "\"";
    }

    private double calculateConfidence(List<RetrievedChunk> chunks, String version) {
        if (chunks.isEmpty()) return 0.0;
        double maxSim = chunks.get(0).getSimilarity();
        double avgTop3 = chunks.stream().limit(3)
            .mapToDouble(RetrievedChunk::getSimilarity).average().orElse(0.0);
        double versionScore = (version != null
            && chunks.stream().allMatch(c -> version.equals(c.getVersion()))) ? 1.0 : 0.0;
        double corroboration = chunks.size() >= 3 ? 1.0 : 0.0;
        return (maxSim * 0.4) + (avgTop3 * 0.3) + (versionScore * 0.2) + (corroboration * 0.1);
    }

    private List<SourceReference> buildSources(List<RetrievedChunk> chunks) {
        return chunks.stream()
            .map(chunk -> SourceReference.builder()
                .document(chunk.getDocumentName())
                .chunkId(chunk.getChunkId())
                .relevanceScore(chunk.getSimilarity())
                .product(chunk.getProduct())
                .version(chunk.getVersion())
                .excerpt(chunk.getContent().length() > 200
                    ? chunk.getContent().substring(0, 200) + "…"
                    : chunk.getContent())
                .build())
            .collect(Collectors.toList());
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
            .tenantId(TenantContext.get())
            .product(product)
            .version(version)
            .messageCount(0)
            .build();
        return sessionRepository.save(session);
    }

    private ChatMessage saveMessage(UUID chatId, ChatMessage.Role role, String content) {
        ChatMessage message = ChatMessage.builder()
            .chatId(chatId)
            .role(role)
            .content(content)
            .build();
        return messageRepository.save(message);
    }

    private String enhanceQueryWithContext(String question, String context) {
        if (context == null || context.isEmpty()) return question;
        return question + "\n\n(Context from previous conversation:\n" +
               context.substring(0, Math.min(500, context.length())) + ")";
    }

    @Transactional(readOnly = true)
    public ChatHistoryResponse getChatHistory(String chatIdStr, UUID userId) {
        UUID chatId = UUID.fromString(chatIdStr);
        List<ChatMessage> messages = messageRepository.findByChatIdOrderByCreatedAtAsc(chatId);
        List<ChatMessageDTO> messageDTOs = messages.stream()
            .map(msg -> {
                long upvoteCount = 0;
                boolean userUpvoted = false;
                if (msg.getRole() == ChatMessage.Role.ASSISTANT) {
                    upvoteCount = upvoteRepository.countByChatMessageId(msg.getId());
                    userUpvoted = userId != null
                        && upvoteRepository.existsByChatMessageIdAndUserId(msg.getId(), userId);
                }
                return ChatMessageDTO.builder()
                    .id(msg.getId().toString())
                    .role(msg.getRole().toString())
                    .content(msg.getContent())
                    .createdAt(msg.getCreatedAt())
                    .upvoteCount(upvoteCount)
                    .userUpvoted(userUpvoted)
                    .build();
            })
            .collect(Collectors.toList());
        return ChatHistoryResponse.builder()
            .chatId(chatId.toString())
            .messageCount(messageDTOs.size())
            .messages(messageDTOs)
            .build();
    }

    @Transactional(readOnly = true)
    public AllChatsResponse getAllChatSessions(UUID userId, boolean isAdmin) {
        List<ChatSession> sessions = isAdmin
            ? sessionRepository.findAllByOrderByLastActiveAtDesc()
            : sessionRepository.findByUserIdOrderByPinnedDescLastActiveAtDesc(userId);

        List<ChatSessionDTO> sessionDTOs = sessions.stream()
            .map(session -> ChatSessionDTO.builder()
                .chatId(session.getId().toString())
                .title(session.getTitle())
                .product(session.getProduct())
                .version(session.getVersion())
                .pinned(session.isPinned())
                .tags(session.getTags() != null ? Arrays.asList(session.getTags()) : Collections.emptyList())
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
        UUID chatId = UUID.fromString(chatIdStr);
        messageRepository.deleteByChatId(chatId);
        summaryRepository.deleteById(chatId);
        sessionRepository.deleteById(chatId);
        log.info("Deleted chat session: {}", chatId);
    }

    // ── DTOs ──────────────────────────────────────────────────────────────────

    @lombok.Data @lombok.Builder
    public static class ChatRequest {
        private String chatId;
        private String product;
        private String version;
        private String question;
        private UUID userId;
    }

    @lombok.Data @lombok.Builder
    public static class ChatResponse {
        private String chatId;
        private String messageId;
        private String answer;
        private List<SourceReference> sources;
        private double confidence;
        private String sessionTitle;
        private List<String> relatedQuestions;
        private List<ReasoningStep> reasoningChain;
    }

    @lombok.Data @lombok.Builder
    public static class ReasoningStep {
        private String subQuestion;
        private int chunksFound;
        private double maxSimilarity;
    }

    @lombok.Data @lombok.Builder
    public static class SourceReference {
        private String document;
        private String chunkId;
        private double relevanceScore;
        private String product;
        private String version;
        private String excerpt;
    }

    @lombok.Data @lombok.Builder
    public static class SessionUpdateResponse {
        private String chatId;
        private String title;
        private boolean pinned;
        private String[] tags;
    }

    @lombok.Data @lombok.Builder
    public static class ChatHistoryResponse {
        private String chatId;
        private int messageCount;
        private List<ChatMessageDTO> messages;
    }

    @lombok.Data @lombok.Builder
    public static class ChatMessageDTO {
        private String id;
        private String role;
        private String content;
        private LocalDateTime createdAt;
        private long upvoteCount;
        private boolean userUpvoted;
    }

    @lombok.Data @lombok.Builder
    public static class AllChatsResponse {
        private int totalChats;
        private List<ChatSessionDTO> sessions;
    }

    @lombok.Data @lombok.Builder
    public static class ChatSessionDTO {
        private String chatId;
        private String title;
        private String product;
        private String version;
        private boolean pinned;
        private List<String> tags;
        private Integer messageCount;
        private LocalDateTime createdAt;
        private LocalDateTime lastActiveAt;
    }
}

package com.docai.bot.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.docai.bot.application.service.AnalyticsService;
import com.docai.bot.application.service.AnswerGenerationService;
import com.docai.bot.application.service.ChatService;
import com.docai.bot.application.service.ChatService.ChatRequest;
import com.docai.bot.application.service.ChatService.ChatResponse;
import com.docai.bot.application.service.ChatSummaryService;
import com.docai.bot.application.service.ContextManager;
import com.docai.bot.application.service.DocumentAccessPolicy;
import com.docai.bot.application.service.MultiHopReasoningService;
import com.docai.bot.application.service.PeopleAlsoAskedService;
import com.docai.bot.application.service.QueryAnalyzerService;
import com.docai.bot.application.service.UserPreferenceService;
import com.docai.bot.application.service.VectorSearchService;
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

@ExtendWith(MockitoExtension.class)
class ChatServiceTest {

    @Mock ChatSessionRepository sessionRepository;
    @Mock ChatMessageRepository messageRepository;
    @Mock ChatSummaryRepository summaryRepository;
    @Mock AnswerUpvoteRepository upvoteRepository;
    @Mock VectorSearchService vectorSearchService;
    @Mock DocumentAccessPolicy documentAccessPolicy;
    @Mock ContextManager contextManager;
    @Mock ChatSummaryService summaryService;
    @Mock AnswerGenerationService answerService;
    @Mock MultiHopReasoningService multiHopService;
    @Mock PeopleAlsoAskedService peopleAlsoAskedService;
    @Mock QueryAnalyzerService queryAnalyzer;
    @Mock UserPreferenceService preferenceService;
    @Mock AnalyticsService analyticsService;

    private ChatService chatService;
    private final UUID userId = UUID.randomUUID();
    private final UUID sessionId = UUID.randomUUID();
    private final UUID tenantId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        TenantContext.set(tenantId);
        chatService = new ChatService(
            sessionRepository, messageRepository, summaryRepository,
            upvoteRepository, vectorSearchService, documentAccessPolicy, contextManager,
            summaryService, answerService, multiHopService,
            peopleAlsoAskedService, queryAnalyzer, preferenceService, analyticsService
        );
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void processQuery_newSession_createsSessionAndReturnsAnswer() {
        stubNewSession();
        stubDependencies("This is the answer.");

        ChatRequest req = ChatRequest.builder()
            .question("What is the installation path?")
            .product("product-a")
            .version("1.0")
            .userId(userId)
            .build();

        ChatResponse resp = chatService.processQuery(req);

        assertThat(resp.getAnswer()).isEqualTo("This is the answer.");
        assertThat(resp.getChatId()).isNotNull();
        verify(sessionRepository, times(2)).save(any(ChatSession.class));
    }

    @Test
    void processQuery_firstExchange_setsSessionTitle() {
        ChatSession session = stubNewSession();
        session.setMessageCount(0);
        stubDependencies("Answer text.");
        when(answerService.generateSessionTitle(anyString(), anyString())).thenReturn("Installing Product A");

        ChatRequest req = ChatRequest.builder()
            .question("How to install?")
            .product("product-a")
            .version("1.0")
            .userId(userId)
            .build();

        ChatResponse resp = chatService.processQuery(req);

        assertThat(resp.getSessionTitle()).isEqualTo("Installing Product A");
    }

    @Test
    void processQuery_existingSession_resumesSession() {
        ChatSession existing = ChatSession.builder()
            .userId(userId).product("product-a").version("1.0").messageCount(4).build();
        setId(existing, sessionId);
        when(sessionRepository.findById(sessionId)).thenReturn(Optional.of(existing));
        stubDependencies("Answer.");

        ChatRequest req = ChatRequest.builder()
            .chatId(sessionId.toString())
            .question("Follow-up?")
            .userId(userId)
            .build();

        ChatResponse resp = chatService.processQuery(req);

        assertThat(resp.getChatId()).isEqualTo(sessionId.toString());
        assertThat(resp.getSessionTitle()).isNull();  // not first exchange
    }

    @Test
    void processQuery_confidenceCalculatedFromChunks() {
        stubNewSession();

        RetrievedChunk chunk = RetrievedChunk.builder()
            .chunkId("c1").content("content").documentName("doc.pdf")
            .similarity(0.9).product("product-a").version("1.0").build();
        when(documentAccessPolicy.resolveScope(any(), any()))
            .thenReturn(new SearchScope(tenantId, Set.of(UUID.randomUUID())));
        when(vectorSearchService.search(anyString(), any(SearchScope.class))).thenReturn(List.of(chunk));
        when(queryAnalyzer.analyzeQuery(anyString(), any())).thenReturn(new QueryAnalyzerService.QueryContext());
        when(preferenceService.getPreferences(any())).thenReturn(defaultPrefs());
        when(contextManager.buildContextPrompt(any())).thenReturn("");
        when(multiHopService.isComplexQuery(anyString())).thenReturn(false);
        when(answerService.generateAnswer(any(), any(), any(), any(), any()))
            .thenReturn(new AnswerGenerationService.AnswerResult("Answer.", List.of(), 10, 5));
        when(answerService.generateSessionTitle(anyString(), anyString())).thenReturn("Title");
        stubMessageSave();
        when(peopleAlsoAskedService.getPeopleAlsoAsked(any(), any(), any(), any())).thenReturn(List.of());

        ChatRequest req = ChatRequest.builder()
            .question("question?").product("product-a").version("1.0").userId(userId).build();

        ChatResponse resp = chatService.processQuery(req);

        assertThat(resp.getConfidence()).isGreaterThan(0.0);
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private ChatSession stubNewSession() {
        ChatSession session = ChatSession.builder()
            .userId(userId).product("product-a").version("1.0").messageCount(0).build();
        setId(session, sessionId);
        when(sessionRepository.save(any(ChatSession.class))).thenReturn(session);
        return session;
    }

    private void stubDependencies(String answer) {
        when(documentAccessPolicy.resolveScope(any(), any()))
            .thenReturn(new SearchScope(tenantId, Set.of(UUID.randomUUID())));
        when(queryAnalyzer.analyzeQuery(anyString(), any())).thenReturn(new QueryAnalyzerService.QueryContext());
        when(preferenceService.getPreferences(any())).thenReturn(defaultPrefs());
        when(contextManager.buildContextPrompt(any())).thenReturn("");
        when(multiHopService.isComplexQuery(anyString())).thenReturn(false);
        when(vectorSearchService.search(anyString(), any(SearchScope.class))).thenReturn(List.of());
        when(answerService.generateAnswer(any(), any(), any(), any(), any()))
            .thenReturn(new AnswerGenerationService.AnswerResult(answer, List.of(), 10, 5));
        stubMessageSave();
        when(peopleAlsoAskedService.getPeopleAlsoAsked(any(), any(), any(), any())).thenReturn(List.of());
    }

    private void stubMessageSave() {
        ArgumentCaptor<ChatMessage> captor = ArgumentCaptor.forClass(ChatMessage.class);
        when(messageRepository.save(any(ChatMessage.class))).thenAnswer(inv -> {
            ChatMessage m = inv.getArgument(0);
            setId(m, UUID.randomUUID());
            return m;
        });
    }

    private static UserPreference defaultPrefs() {
        UserPreference p = new UserPreference();
        p.setVerbosity("BALANCED");
        p.setAnswerFormat("PROSE");
        return p;
    }

    private static <T> void setId(T entity, UUID id) {
        try {
            var field = entity.getClass().getDeclaredField("id");
            field.setAccessible(true);
            field.set(entity, id);
        } catch (Exception ignored) {}
    }
}

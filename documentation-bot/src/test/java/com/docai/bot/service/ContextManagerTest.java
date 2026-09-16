package com.docai.bot.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import com.docai.bot.application.service.ContextManager;
import com.docai.bot.application.service.LLMRouter;
import com.docai.bot.domain.entity.ChatMessage;
import com.docai.bot.domain.repository.ChatMessageRepository;
import com.docai.bot.domain.repository.ChatSummaryRepository;

/** Covers ContextManager#rewriteForRetrieval — condensing a follow-up into a standalone query
 * before retrieval, without ever making retrieval worse than searching on the raw question. */
@ExtendWith(MockitoExtension.class)
class ContextManagerTest {

    @Mock ChatMessageRepository messageRepository;
    @Mock ChatSummaryRepository summaryRepository;
    @Mock LLMRouter llmRouter;

    private ContextManager contextManager;
    private final UUID chatId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        contextManager = new ContextManager(messageRepository, summaryRepository, llmRouter);
        // @Value fields are only populated by Spring's bean post-processing, which a plain `new`
        // here never goes through — set the defaults explicitly so tests see the same behavior
        // the real bean would have.
        ReflectionTestUtils.setField(contextManager, "rewriteEnabled", true);
        ReflectionTestUtils.setField(contextManager, "rewriteContextMessages", 6);
        ReflectionTestUtils.setField(contextManager, "rewriteMaxChars", 400);
    }

    // ── The gate (dependsOnContext) ──────────────────────────────────────────

    @ParameterizedTest
    @ValueSource(strings = {
        "what about on Windows?",
        "and in staging?",
        "how about the API?",
        "why not?",
        "explain more",
        "is it free",
        "tell me more"
    })
    void dependsOnContext_continuationOrAnaphora_true(String question) {
        assertThat(ContextManager.dependsOnContext(question)).isTrue();
    }

    @Test
    void dependsOnContext_shortQuestion_true() {
        assertThat(ContextManager.dependsOnContext("what is it")).isTrue();
    }

    @Test
    void dependsOnContext_longSelfContainedQuestion_false() {
        assertThat(ContextManager.dependsOnContext(
            "How do I install Case360 on a Windows Server 2019 machine?")).isFalse();
    }

    @Test
    void dependsOnContext_blank_false() {
        assertThat(ContextManager.dependsOnContext("   ")).isFalse();
    }

    // ── rewriteForRetrieval ───────────────────────────────────────────────────

    @Test
    void rewriteForRetrieval_selfContainedQuestion_neverCallsLlm() {
        String question = "How do I install Case360 on a Windows Server 2019 machine?";

        String result = contextManager.rewriteForRetrieval(chatId, question);

        assertThat(result).isEqualTo(question);
        verify(llmRouter, never()).chat(anyString(), anyBoolean());
        verify(messageRepository, never()).findRecentMessages(any(), anyInt());
    }

    @Test
    void rewriteForRetrieval_noHistoryYet_returnsQuestionUnchanged() {
        when(messageRepository.findRecentMessages(any(), anyInt())).thenReturn(List.of());

        String result = contextManager.rewriteForRetrieval(chatId, "what about on Windows?");

        assertThat(result).isEqualTo("what about on Windows?");
        verify(llmRouter, never()).chat(anyString(), anyBoolean());
    }

    @Test
    void rewriteForRetrieval_disabled_returnsQuestionUnchangedWithoutTouchingHistory() {
        ReflectionTestUtils.setField(contextManager, "rewriteEnabled", false);

        String result = contextManager.rewriteForRetrieval(chatId, "what about on Windows?");

        assertThat(result).isEqualTo("what about on Windows?");
        verify(messageRepository, never()).findRecentMessages(any(), anyInt());
    }

    @Test
    void rewriteForRetrieval_withHistory_returnsSanitizedLlmRewrite() {
        when(messageRepository.findRecentMessages(any(), anyInt())).thenReturn(priorExchange(
            "How do I install Case360?", "Download the installer and run it as administrator."));
        when(llmRouter.chat(anyString(), eq(false)))
            .thenReturn("How do I install Case360 on Windows?");

        String result = contextManager.rewriteForRetrieval(chatId, "what about on Windows?");

        assertThat(result).isEqualTo("How do I install Case360 on Windows?");
    }

    @Test
    void rewriteForRetrieval_llmThrows_fallsBackToOriginalQuestion() {
        when(messageRepository.findRecentMessages(any(), anyInt())).thenReturn(priorExchange(
            "How do I install Case360?", "Download the installer and run it as administrator."));
        when(llmRouter.chat(anyString(), eq(false))).thenThrow(new RuntimeException("LLM unavailable"));

        String result = contextManager.rewriteForRetrieval(chatId, "what about on Windows?");

        assertThat(result).isEqualTo("what about on Windows?");
    }

    @Test
    void rewriteForRetrieval_llmReturnsAnAnswerInsteadOfARewrite_discardedAsTooLong() {
        when(messageRepository.findRecentMessages(any(), anyInt())).thenReturn(priorExchange(
            "How do I install Case360?", "Download the installer and run it as administrator."));
        String rambling = "Well, ".repeat(100) + "you would install it on Windows the same way.";
        when(llmRouter.chat(anyString(), eq(false))).thenReturn(rambling);

        String result = contextManager.rewriteForRetrieval(chatId, "what about on Windows?");

        assertThat(result).isEqualTo("what about on Windows?");
    }

    @Test
    void rewriteForRetrieval_llmReplyStripsLabelPrefixAndQuotes() {
        when(messageRepository.findRecentMessages(any(), anyInt())).thenReturn(priorExchange(
            "How do I install Case360?", "Download the installer and run it as administrator."));
        when(llmRouter.chat(anyString(), eq(false)))
            .thenReturn("Standalone question: \"How do I install Case360 on Windows?\"");

        String result = contextManager.rewriteForRetrieval(chatId, "what about on Windows?");

        assertThat(result).isEqualTo("How do I install Case360 on Windows?");
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private static List<ChatMessage> priorExchange(String question, String answer) {
        LocalDateTime now = LocalDateTime.now();
        return List.of(
            ChatMessage.builder().role(ChatMessage.Role.USER).content(question).createdAt(now.minusMinutes(2)).build(),
            ChatMessage.builder().role(ChatMessage.Role.ASSISTANT).content(answer).createdAt(now.minusMinutes(1)).build());
    }
}

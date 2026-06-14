package com.docai.bot.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.ai.chat.client.ChatClient.CallResponseSpec;
import org.springframework.ai.chat.client.ChatClient.ChatClientRequestSpec;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.metadata.ChatResponseMetadata;
import org.springframework.ai.chat.metadata.Usage;

import com.docai.bot.application.service.AnswerGenerationService;
import com.docai.bot.domain.model.RetrievedChunk;

import io.github.resilience4j.bulkhead.Bulkhead;
import io.github.resilience4j.bulkhead.BulkheadConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;

@ExtendWith(MockitoExtension.class)
class AnswerGenerationServiceTest {

    @Mock ChatClient.Builder chatClientBuilder;
    @Mock ChatClient chatClient;
    @Mock ChatClientRequestSpec requestSpec;
    @Mock CallResponseSpec callSpec;
    @Mock ChatResponse chatResponse;
    @Mock Generation generation;
    @Mock ChatResponseMetadata metadata;
    @Mock Usage usage;

    private AnswerGenerationService service;

    @BeforeEach
    void setUp() {
        CircuitBreaker cb = CircuitBreaker.of("test", CircuitBreakerConfig.ofDefaults());
        Bulkhead bh = Bulkhead.of("test", BulkheadConfig.ofDefaults());
        service = new AnswerGenerationService(chatClientBuilder, cb, bh);
        ReflectionTestUtils.setField(service, "minSimilarityThreshold", 0.55);
    }

    @Test
    void generateAnswer_belowThreshold_returnsEmptyStateMessage() {
        RetrievedChunk lowScoreChunk = RetrievedChunk.builder()
            .chunkId("c1")
            .content("some content")
            .documentName("doc.pdf")
            .similarity(0.3)   // below default 0.55 threshold
            .product("product-a")
            .version("1.0")
            .build();

        var result = service.generateAnswer("question?", null, List.of(lowScoreChunk), "BALANCED", "PROSE");

        assertThat(result.answer()).contains("couldn't find reliable documentation");
        assertThat(result.relatedQuestions()).isEmpty();
    }

    @Test
    void generateAnswer_noChunks_returnsEmptyStateMessage() {
        var result = service.generateAnswer("question?", null, List.of(), "BALANCED", "PROSE");

        assertThat(result.answer()).contains("couldn't find reliable documentation");
        assertThat(result.relatedQuestions()).isEmpty();
        assertThat(result.promptTokens()).isZero();
        assertThat(result.completionTokens()).isZero();
    }

    @Test
    void generateAnswer_aboveThreshold_callsLlmAndParsesFollowUps() {
        stubLlmResponse("Here is the answer.\n---FOLLOW-UP-QUESTIONS---\nQ1?\nQ2?\nQ3?");

        RetrievedChunk chunk = highSimilarityChunk();
        var result = service.generateAnswer("question?", null, List.of(chunk), "BALANCED", "PROSE");

        assertThat(result.answer()).isEqualTo("Here is the answer.");
        assertThat(result.relatedQuestions()).containsExactly("Q1?", "Q2?", "Q3?");
    }

    @Test
    void generateAnswer_llmReturnsNoMarker_returnsFullTextAsAnswer() {
        stubLlmResponse("Simple answer with no follow-up marker.");

        var result = service.generateAnswer("question?", null, List.of(highSimilarityChunk()), "BALANCED", "PROSE");

        assertThat(result.answer()).isEqualTo("Simple answer with no follow-up marker.");
        assertThat(result.relatedQuestions()).isEmpty();
    }

    @Test
    void generateSessionTitle_returnsLlmTitle() {
        stubSimpleLlmResponse("Installing Case360 on Windows Server");

        String title = service.generateSessionTitle("How do I install Case360?", "Step 1: ...");

        assertThat(title).isEqualTo("Installing Case360 on Windows Server");
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private void stubLlmResponse(String content) {
        when(chatClientBuilder.build()).thenReturn(chatClient);
        when(chatClient.prompt()).thenReturn(requestSpec);
        when(requestSpec.user(any(String.class))).thenReturn(requestSpec);
        when(requestSpec.call()).thenReturn(callSpec);
        when(callSpec.chatResponse()).thenReturn(chatResponse);
        when(chatResponse.getResult()).thenReturn(generation);
        when(generation.getOutput()).thenReturn(
            new org.springframework.ai.chat.messages.AssistantMessage(content));
        when(chatResponse.getMetadata()).thenReturn(metadata);
        when(metadata.getUsage()).thenReturn(usage);
        when(usage.getPromptTokens()).thenReturn(100);
        when(usage.getCompletionTokens()).thenReturn(50);
    }

    private void stubSimpleLlmResponse(String content) {
        when(chatClientBuilder.build()).thenReturn(chatClient);
        when(chatClient.prompt()).thenReturn(requestSpec);
        when(requestSpec.user(any(String.class))).thenReturn(requestSpec);
        when(requestSpec.call()).thenReturn(callSpec);
        when(callSpec.content()).thenReturn(content);
    }

    private static RetrievedChunk highSimilarityChunk() {
        return RetrievedChunk.builder()
            .chunkId("c1")
            .content("Relevant documentation content.")
            .documentName("guide.pdf")
            .similarity(0.85)
            .product("product-a")
            .version("2.0")
            .build();
    }
}

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
import org.springframework.test.util.ReflectionTestUtils;

import com.docai.bot.application.service.LLMRouter;
import com.docai.bot.application.service.ReRankingService;
import com.docai.bot.application.service.ReRankingService.ScoredCandidate;
import com.docai.bot.domain.model.RetrievedChunk;

import io.github.resilience4j.bulkhead.Bulkhead;
import io.github.resilience4j.bulkhead.BulkheadConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;

@ExtendWith(MockitoExtension.class)
class ReRankingServiceTest {

    @Mock LLMRouter llmRouter;

    private ReRankingService service;

    @BeforeEach
    void setUp() {
        CircuitBreaker cb = CircuitBreaker.of("test", CircuitBreakerConfig.ofDefaults());
        Bulkhead bh = Bulkhead.of("test", BulkheadConfig.ofDefaults());
        service = new ReRankingService(llmRouter, cb, bh);
    }

    // ── MMR (diversity) ─────────────────────────────────────────────────────

    @Test
    void mmr_prefersDiverseCandidateOverNearDuplicateOfAlreadySelected() {
        ReflectionTestUtils.setField(service, "llmRerankEnabled", false);

        float[] query = {1f, 0f, 0f};
        double a1 = Math.toRadians(20), a2 = Math.toRadians(21);
        float[] aEmb = {(float) Math.cos(a1), (float) Math.sin(a1), 0f};
        float[] bEmb = {(float) Math.cos(a2), (float) Math.sin(a2), 0f}; // near-duplicate of A
        float[] cEmb = {(float) Math.cos(a1), (float) -Math.sin(a1), 0f}; // same relevance as A, different direction

        ScoredCandidate a = candidate("A", aEmb, query);
        ScoredCandidate b = candidate("B", bEmb, query);
        ScoredCandidate c = candidate("C", cEmb, query);

        List<RetrievedChunk> result = service.rerank("question", query, List.of(a, b, c), 2);

        assertThat(result).extracting(RetrievedChunk::getChunkId).containsExactly("A", "C");
    }

    @Test
    void mmr_respectsFinalTopKLimit() {
        ReflectionTestUtils.setField(service, "llmRerankEnabled", false);

        float[] query = {1f, 0f};
        List<ScoredCandidate> candidates = List.of(
            candidate("1", new float[]{1f, 0f}, query),
            candidate("2", new float[]{0.9f, 0.1f}, query),
            candidate("3", new float[]{0.8f, 0.2f}, query),
            candidate("4", new float[]{0.7f, 0.3f}, query)
        );

        List<RetrievedChunk> result = service.rerank("question", query, candidates, 2);

        assertThat(result).hasSize(2);
    }

    @Test
    void singleCandidate_returnedAsIs_withoutInvokingLlm() {
        float[] query = {1f, 0f};
        ScoredCandidate only = candidate("solo", new float[]{1f, 0f}, query);

        List<RetrievedChunk> result = service.rerank("question", query, List.of(only), 5);

        assertThat(result).extracting(RetrievedChunk::getChunkId).containsExactly("solo");
    }

    // ── LLM re-rank pass ────────────────────────────────────────────────────

    @Test
    void llmRerank_reordersAccordingToLlmResponse() {
        ReflectionTestUtils.setField(service, "llmRerankEnabled", true);
        stubLlmResponse("2,1");

        float[] query = {1f, 0f};
        List<ScoredCandidate> candidates = List.of(
            candidate("first", new float[]{1f, 0f}, query),
            candidate("second", new float[]{0.9f, 0.1f}, query)
        );

        List<RetrievedChunk> result = service.rerank("question", query, candidates, 2);

        assertThat(result).extracting(RetrievedChunk::getChunkId).containsExactly("second", "first");
    }

    @Test
    void llmRerank_malformedResponse_fallsBackToMmrOrder() {
        ReflectionTestUtils.setField(service, "llmRerankEnabled", true);
        stubLlmResponse("not a valid ordering at all");

        float[] query = {1f, 0f};
        List<ScoredCandidate> candidates = List.of(
            candidate("first", new float[]{1f, 0f}, query),
            candidate("second", new float[]{0.9f, 0.1f}, query)
        );

        List<RetrievedChunk> result = service.rerank("question", query, candidates, 2);

        // MMR already put the more-relevant one first; malformed LLM output changes nothing.
        assertThat(result).extracting(RetrievedChunk::getChunkId).containsExactly("first", "second");
    }

    @Test
    void llmRerank_circuitBreakerOpen_fallsBackToMmrOrderWithoutThrowing() {
        ReflectionTestUtils.setField(service, "llmRerankEnabled", true);
        CircuitBreaker openCb = CircuitBreaker.of("open-test", CircuitBreakerConfig.custom()
            .slidingWindowSize(1).failureRateThreshold(0.01f).build());
        openCb.transitionToOpenState();
        Bulkhead bh = Bulkhead.of("test2", BulkheadConfig.ofDefaults());
        ReRankingService serviceWithOpenBreaker = new ReRankingService(llmRouter, openCb, bh);

        float[] query = {1f, 0f};
        List<ScoredCandidate> candidates = List.of(
            candidate("first", new float[]{1f, 0f}, query),
            candidate("second", new float[]{0.9f, 0.1f}, query)
        );

        List<RetrievedChunk> result = serviceWithOpenBreaker.rerank("question", query, candidates, 2);

        assertThat(result).extracting(RetrievedChunk::getChunkId).containsExactly("first", "second");
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private ScoredCandidate candidate(String id, float[] embedding, float[] query) {
        RetrievedChunk chunk = RetrievedChunk.builder()
            .chunkId(id)
            .content("content for " + id)
            .documentName("doc.pdf")
            .similarity(com.docai.bot.domain.model.CosineSimilarity.of(query, embedding))
            .build();
        return new ScoredCandidate(chunk, embedding);
    }

    private void stubLlmResponse(String content) {
        when(llmRouter.chat(any(String.class), org.mockito.ArgumentMatchers.anyBoolean())).thenReturn(content);
    }
}

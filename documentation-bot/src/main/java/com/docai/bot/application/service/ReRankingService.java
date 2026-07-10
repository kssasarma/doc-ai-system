package com.docai.bot.application.service;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.docai.bot.domain.model.CosineSimilarity;
import com.docai.bot.domain.model.RetrievedChunk;

import io.github.resilience4j.bulkhead.Bulkhead;
import io.github.resilience4j.bulkhead.BulkheadFullException;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import lombok.extern.slf4j.Slf4j;

/**
 * Narrows a fused (dense + lexical) candidate pool down to the final result set the LLM actually
 * sees, in two stages:
 *
 * <ol>
 *   <li><b>MMR (Maximal Marginal Relevance)</b> — always applied. Greedily picks candidates that
 *       balance relevance to the query against dissimilarity from what's already been picked, so
 *       the final set isn't dominated by several near-duplicate chunks all covering the same
 *       sentence. Pure vector math against the candidates' own embeddings — no extra LLM call.</li>
 *   <li><b>LLM relevance re-rank</b> — optional (config-gated, on by default), one extra LLM call
 *       that judges the MMR-selected set directly against the question and reorders it. Best
 *       effort: any failure (circuit open, bulkhead full, malformed response) falls back to the
 *       MMR order unchanged rather than blocking the answer.</li>
 * </ol>
 */
@Slf4j
@Service
public class ReRankingService {

    private final ChatClient.Builder chatClientBuilder;
    private final CircuitBreaker llmCircuitBreaker;
    private final Bulkhead llmBulkhead;

    @Value("${bot.rerank.mmr-lambda:0.7}")
    private double mmrLambda;

    @Value("${bot.rerank.llm-enabled:true}")
    private boolean llmRerankEnabled;

    public ReRankingService(ChatClient.Builder chatClientBuilder,
                             @Qualifier("llmCircuitBreaker") CircuitBreaker llmCircuitBreaker,
                             @Qualifier("llmBulkhead") Bulkhead llmBulkhead) {
        this.chatClientBuilder = chatClientBuilder;
        this.llmCircuitBreaker = llmCircuitBreaker;
        this.llmBulkhead = llmBulkhead;
    }

    /** A fused candidate paired with its raw embedding — needed only transiently, for MMR math. */
    public record ScoredCandidate(RetrievedChunk chunk, float[] embedding) {}

    public List<RetrievedChunk> rerank(String query, float[] queryEmbedding,
                                        List<ScoredCandidate> candidates, int finalTopK) {
        List<ScoredCandidate> mmrOrdered = mmr(queryEmbedding, candidates, finalTopK);
        List<RetrievedChunk> mmrChunks = mmrOrdered.stream().map(ScoredCandidate::chunk).toList();

        if (!llmRerankEnabled || mmrChunks.size() <= 1) {
            return mmrChunks;
        }
        return llmRerank(query, mmrChunks);
    }

    private List<ScoredCandidate> mmr(float[] queryEmbedding, List<ScoredCandidate> candidates, int topK) {
        List<ScoredCandidate> remaining = new ArrayList<>(candidates);
        List<ScoredCandidate> selected = new ArrayList<>();

        while (!remaining.isEmpty() && selected.size() < topK) {
            ScoredCandidate best = null;
            double bestScore = Double.NEGATIVE_INFINITY;
            for (ScoredCandidate candidate : remaining) {
                double relevance = CosineSimilarity.of(queryEmbedding, candidate.embedding());
                double maxSimToSelected = selected.stream()
                    .mapToDouble(s -> CosineSimilarity.of(candidate.embedding(), s.embedding()))
                    .max().orElse(0.0);
                double mmrScore = mmrLambda * relevance - (1 - mmrLambda) * maxSimToSelected;
                if (mmrScore > bestScore) {
                    bestScore = mmrScore;
                    best = candidate;
                }
            }
            selected.add(best);
            remaining.remove(best);
        }
        return selected;
    }

    private List<RetrievedChunk> llmRerank(String query, List<RetrievedChunk> candidates) {
        String prompt = buildRerankPrompt(query, candidates);
        try {
            String response = llmBulkhead.executeSupplier(
                () -> llmCircuitBreaker.executeSupplier(
                    () -> chatClientBuilder.build().prompt().user(prompt).call().content()
                )
            );
            return applyOrder(candidates, parseOrder(response, candidates.size()));
        } catch (CallNotPermittedException e) {
            log.warn("LLM re-rank skipped — circuit breaker open: {}", e.getMessage());
            return candidates;
        } catch (BulkheadFullException e) {
            log.warn("LLM re-rank skipped — bulkhead full: {}", e.getMessage());
            return candidates;
        } catch (Exception e) {
            log.warn("LLM re-rank failed, keeping MMR order: {}", e.getMessage());
            return candidates;
        }
    }

    private String buildRerankPrompt(String query, List<RetrievedChunk> candidates) {
        StringBuilder sb = new StringBuilder();
        sb.append("Rank these documentation excerpts by how directly and completely they answer the question below.\n");
        sb.append("Question: ").append(query).append("\n\n");
        for (int i = 0; i < candidates.size(); i++) {
            sb.append('[').append(i + 1).append("] ").append(truncate(candidates.get(i).getContent(), 500)).append("\n\n");
        }
        sb.append("Respond with ONLY the excerpt numbers in order from most to least relevant, comma-separated (e.g. \"3,1,2\"). No other text.");
        return sb.toString();
    }

    private List<Integer> parseOrder(String response, int expectedCount) {
        if (response == null || response.isBlank()) return List.of();
        try {
            return Arrays.stream(response.trim().split("[,\\s]+"))
                .filter(s -> !s.isBlank())
                .map(Integer::parseInt)
                .limit(expectedCount)
                .toList();
        } catch (NumberFormatException e) {
            return List.of();
        }
    }

    /** Reorders {@code candidates} per {@code order} (1-based indices); anything the LLM didn't
     * mention (or mentioned invalidly) keeps its MMR position, appended after the ordered ones. */
    private List<RetrievedChunk> applyOrder(List<RetrievedChunk> candidates, List<Integer> order) {
        if (order.isEmpty()) return candidates;
        List<RetrievedChunk> reordered = new ArrayList<>();
        Set<Integer> used = new HashSet<>();
        for (int idx : order) {
            if (idx >= 1 && idx <= candidates.size() && used.add(idx)) {
                reordered.add(candidates.get(idx - 1));
            }
        }
        for (int i = 0; i < candidates.size(); i++) {
            if (!used.contains(i + 1)) reordered.add(candidates.get(i));
        }
        return reordered;
    }

    private static String truncate(String text, int maxLen) {
        return text.length() > maxLen ? text.substring(0, maxLen) + "…" : text;
    }
}

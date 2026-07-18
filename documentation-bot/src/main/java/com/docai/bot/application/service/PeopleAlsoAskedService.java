package com.docai.bot.application.service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import com.docai.bot.application.event.ChatQueryRecordedEvent;
import com.docai.bot.domain.entity.QuerySessionGraph;
import com.docai.bot.domain.repository.QuerySessionGraphRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Phase 6.9 — "People Also Asked" from real query data.
 *
 * Records every query in the session graph and retrieves semantically
 * related questions asked by other users in recent sessions.
 * Falls back to LLM-generated follow-ups when real data is sparse.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PeopleAlsoAskedService {

    private static final int LOOKBACK_DAYS = 90;
    private static final int RESULT_LIMIT = 3;
    private static final double SIMILARITY_THRESHOLD = 0.25;

    private final QuerySessionGraphRepository querySessionGraphRepository;

    // Async AND gated on commit — same reasoning as AnalyticsService.onQueryRecorded: this
    // used to be a plain @Async method called mid-transaction, which could race the owning
    // session's own INSERT and intermittently fail the FK to chat_sessions.
    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    // REQUIRES_NEW is mandatory here: the publishing transaction is already committed by the time
    // an AFTER_COMMIT listener runs, so this write needs its own transaction — Spring rejects a
    // plain @Transactional on a @TransactionalEventListener at startup.
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onQueryRecorded(ChatQueryRecordedEvent event) {
        try {
            QuerySessionGraph node = QuerySessionGraph.builder()
                .sessionId(event.sessionId())
                .tenantId(event.tenantId())
                .userId(event.userId())
                .queryText(event.question())
                .product(event.product())
                .version(event.version())
                .askedAt(LocalDateTime.now())
                .build();
            querySessionGraphRepository.save(node);
        } catch (Exception e) {
            log.warn("Failed to record query to session graph: {}", e.getMessage());
        }
    }

    /**
     * Returns up to 3 unique questions that other users asked in the same product/version
     * context recently. Uses simple n-gram/keyword overlap as a lightweight similarity proxy
     * (no additional embedding call needed).
     */
    @Transactional(readOnly = true)
    public List<String> getPeopleAlsoAsked(String currentQuery, UUID tenantId, UUID currentSessionId,
                                            String product, String version) {
        if (currentQuery == null || currentQuery.isBlank()) return List.of();

        LocalDateTime since = LocalDateTime.now().minusDays(LOOKBACK_DAYS);
        List<QuerySessionGraph> candidates = querySessionGraphRepository
            .findRecentQueriesForProduct(tenantId, product, version, since, currentSessionId);

        if (candidates.size() < 5) {
            // Not enough data yet — caller will use LLM-generated questions instead
            return List.of();
        }

        String[] currentTokens = tokenize(currentQuery);
        List<ScoredQuery> scored = new ArrayList<>();

        for (QuerySessionGraph candidate : candidates) {
            if (currentQuery.equalsIgnoreCase(candidate.getQueryText())) continue;
            double sim = jaccardSimilarity(currentTokens, tokenize(candidate.getQueryText()));
            if (sim > SIMILARITY_THRESHOLD) {
                scored.add(new ScoredQuery(candidate.getQueryText(), sim));
            }
        }

        // Deduplicate by near-identical text, return top-N by similarity
        return scored.stream()
            .sorted((a, b) -> Double.compare(b.score(), a.score()))
            .map(ScoredQuery::query)
            .distinct()
            .filter(q -> !tooSimilar(q, currentQuery))
            .limit(RESULT_LIMIT)
            .collect(Collectors.toList());
    }

    private record ScoredQuery(String query, double score) {}

    private static String[] tokenize(String text) {
        return text.toLowerCase()
            .replaceAll("[^a-z0-9\\s]", " ")
            .split("\\s+");
    }

    private static double jaccardSimilarity(String[] a, String[] b) {
        var setA = java.util.Set.of(a);
        var setB = java.util.Set.of(b);
        long intersection = setA.stream().filter(setB::contains).count();
        long union = setA.size() + setB.size() - intersection;
        return union == 0 ? 0.0 : (double) intersection / union;
    }

    private static boolean tooSimilar(String a, String b) {
        double sim = jaccardSimilarity(tokenize(a), tokenize(b));
        return sim > 0.85;
    }
}

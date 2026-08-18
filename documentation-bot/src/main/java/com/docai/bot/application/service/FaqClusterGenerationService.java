package com.docai.bot.application.service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.docai.bot.domain.entity.FaqCluster;
import com.docai.bot.domain.entity.FaqEntry;
import com.docai.bot.domain.entity.FaqEntry.Status;
import com.docai.bot.domain.entity.QuerySessionGraph;
import com.docai.bot.domain.model.CosineSimilarity;
import com.docai.bot.domain.model.SearchScope;
import com.docai.bot.domain.repository.DocumentRepository;
import com.docai.bot.domain.repository.FaqClusterRepository;
import com.docai.bot.domain.repository.FaqEntryRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Persists one FAQ cluster per call, each in its own transaction. Split out of
 * {@link AutoFaqService} so that a failure generating one cluster's FAQ entry cannot silently
 * skip the transaction boundary for the whole weekly job — calling through an injected bean goes
 * through Spring's real transactional proxy.
 *
 * Quality improvements over the original implementation:
 *
 * <ul>
 *   <li><b>Cosine centroid</b> — when cluster embeddings are provided, the canonical question is
 *       the item with the highest average cosine similarity to all others (more accurate than
 *       Jaccard when queries were semantically clustered).</li>
 *   <li><b>LLM question polishing</b> — the raw centroid (as typed by a user) is rewritten into a
 *       clear, professional FAQ question before it is shown to users. The raw form is stored in
 *       {@code FaqCluster.canonicalQuestion} for deduplication; the polished form goes into
 *       {@code FaqEntry.question}.</li>
 *   <li><b>Rolling-window deduplication</b> — checks a 60-day window instead of an exact period
 *       match so the same topic can't reappear simply because the generation period rolled over.</li>
 *   <li><b>Rejection-aware deduplication</b> — also checks REJECTED entries in the window; an
 *       admin rejection signals that this topic should not become a public FAQ, and blocks
 *       regeneration of a similar question in future runs.</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FaqClusterGenerationService {

    /**
     * Rolling window for deduplication: wide enough to cover the 30-day lookback of the next
     * generation run plus one full run's worth of buffer, preventing the same topic from
     * reappearing simply because the period date rolled forward.
     */
    private static final int DEDUP_LOOKBACK_DAYS = 60;
    private static final double DEDUP_SIMILARITY_THRESHOLD = 0.6;

    private final FaqClusterRepository faqClusterRepository;
    private final FaqEntryRepository faqEntryRepository;
    private final VectorSearchService vectorSearchService;
    private final AnswerGenerationService answerGenerationService;
    private final DocumentRepository documentRepository;
    private final LLMRouter llmRouter;

    /**
     * Generates and persists a single FAQ entry for the given cluster.
     *
     * @param cluster    queries forming this cluster (size ≥ {@code AutoFaqService.MIN_CLUSTER_SIZE})
     * @param embeddings parallel embeddings for {@code cluster}, same order; empty list signals
     *                   that the Jaccard fallback was used and cosine centroid is unavailable
     * @return           1 if an entry was created, 0 if skipped (duplicate or rejection block)
     */
    @Transactional
    public int generateFaqForCluster(List<QuerySessionGraph> cluster, List<float[]> embeddings,
                                     UUID tenantId, String product, String version,
                                     LocalDate periodStart, LocalDate periodEnd) {

        String rawCanonical = pickCanonical(cluster, embeddings);
        long uniqueUsers = cluster.stream()
            .map(QuerySessionGraph::getUserId).filter(u -> u != null).distinct().count();

        LocalDate dedupWindowStart = periodEnd.minusDays(DEDUP_LOOKBACK_DAYS);
        if (isDuplicate(tenantId, product, version, rawCanonical, dedupWindowStart)) return 0;

        FaqCluster faqCluster = FaqCluster.builder()
            .tenantId(tenantId)
            .product(product)
            .version(version)
            .canonicalQuestion(rawCanonical) // raw centroid — dedup key, never shown to users
            .queryCount(cluster.size())
            .uniqueUsers((int) uniqueUsers)
            .periodStart(periodStart)
            .periodEnd(periodEnd)
            .build();
        faqCluster = faqClusterRepository.save(faqCluster);

        // Tenant-scoped retrieval: admin-level full-corpus visibility, same as GrantBasedDocumentAccessPolicy
        // for ADMIN callers — never the deprecated all-tenants search.
        SearchScope scope = new SearchScope(tenantId, documentRepository.findIdsByTenantId(tenantId))
            .withVersionNarrow(product, version);
        var chunks = vectorSearchService.search(rawCanonical, scope);
        AnswerGenerationService.AnswerResult result =
            answerGenerationService.generateAnswer(rawCanonical, null, chunks, "BALANCED", "PROSE", product, version);

        // Polish the raw centroid query into a proper FAQ question. The raw form is kept in
        // faqCluster.canonicalQuestion for dedup; faqEntry.question is the user-facing phrasing.
        String polishedQuestion = polishQuestion(rawCanonical);

        FaqEntry entry = FaqEntry.builder()
            .clusterId(faqCluster.getId())
            .tenantId(tenantId)
            .question(polishedQuestion)
            .answer(result.answer())
            .product(product)
            .version(version)
            .sources(buildSourcesJson(chunks))
            .status(Status.PENDING)
            .build();
        faqEntryRepository.save(entry);

        log.info("Generated FAQ '{}' (from raw: '{}') — {} queries, {} users",
            polishedQuestion, rawCanonical, cluster.size(), uniqueUsers);
        return 1;
    }

    // ── Deduplication ────────────────────────────────────────────────────────

    /**
     * Returns true if a sufficiently similar question already exists as a cluster or a rejected
     * entry within the rolling dedup window — either case should suppress regeneration.
     */
    private boolean isDuplicate(UUID tenantId, String product, String version,
                                 String rawCanonical, LocalDate dedupWindowStart) {
        String[] canonicalTokens = TextSimilarity.tokenize(rawCanonical);

        // 1. Existing clusters in the rolling window — covers both PENDING/APPROVED duplicates
        //    and the exact-period case the original code handled.
        boolean clusterMatch = faqClusterRepository
            .findRecentClusters(tenantId, product, version, dedupWindowStart)
            .stream()
            .anyMatch(fc -> TextSimilarity.jaccardSimilarity(
                TextSimilarity.tokenize(fc.getCanonicalQuestion()), canonicalTokens) > DEDUP_SIMILARITY_THRESHOLD);
        if (clusterMatch) return true;

        // 2. Rejected entries in the rolling window — an admin rejection signals that this topic
        //    must not become a public FAQ; block regeneration of any similar question.
        return faqEntryRepository
            .findRejectedInWindow(tenantId, product, version, dedupWindowStart.atStartOfDay())
            .stream()
            .anyMatch(e -> TextSimilarity.jaccardSimilarity(
                TextSimilarity.tokenize(e.getQuestion()), canonicalTokens) > DEDUP_SIMILARITY_THRESHOLD);
    }

    // ── Canonical question selection ──────────────────────────────────────────

    /**
     * Picks the most representative query from the cluster — the one with the highest average
     * similarity to all others. Uses cosine similarity over embeddings when available (more
     * accurate for semantically-clustered queries); falls back to Jaccard when not.
     */
    private String pickCanonical(List<QuerySessionGraph> cluster, List<float[]> embeddings) {
        if (embeddings != null && embeddings.size() == cluster.size()) {
            return pickCanonicalByCosine(cluster, embeddings);
        }
        return pickCanonicalByJaccard(cluster);
    }

    private String pickCanonicalByCosine(List<QuerySessionGraph> cluster, List<float[]> embeddings) {
        double bestScore = -1;
        int bestIdx = 0;
        for (int i = 0; i < embeddings.size(); i++) {
            double avgSim = 0;
            for (int j = 0; j < embeddings.size(); j++) {
                avgSim += CosineSimilarity.of(embeddings.get(i), embeddings.get(j));
            }
            avgSim /= embeddings.size();
            if (avgSim > bestScore) { bestScore = avgSim; bestIdx = i; }
        }
        return cluster.get(bestIdx).getQueryText();
    }

    private String pickCanonicalByJaccard(List<QuerySessionGraph> cluster) {
        String[] texts = cluster.stream().map(QuerySessionGraph::getQueryText).toArray(String[]::new);
        double bestScore = -1;
        String best = texts[0];
        for (String candidate : texts) {
            String[] cTok = TextSimilarity.tokenize(candidate);
            double avgSim = 0;
            for (String other : texts) {
                avgSim += TextSimilarity.jaccardSimilarity(cTok, TextSimilarity.tokenize(other));
            }
            avgSim /= texts.length;
            if (avgSim > bestScore) { bestScore = avgSim; best = candidate; }
        }
        return best;
    }

    // ── Question polishing ────────────────────────────────────────────────────

    /**
     * Rewrites the raw centroid query (verbatim user text, often informal or incomplete) into a
     * clear, professional FAQ question. Falls back to the raw form if the LLM call fails so the
     * entry is always generated — a worse question is better than no entry.
     */
    private String polishQuestion(String rawQuestion) {
        String prompt = """
            Rewrite the user query below as a clear, professional FAQ question. \
            Output only the rewritten question — no explanation, no preamble. \
            Start with a capital letter and end with a question mark.

            User query: %s""".formatted(rawQuestion);
        try {
            String polished = llmRouter.chat(prompt, false);
            if (polished != null && !polished.isBlank()) {
                return polished.strip();
            }
        } catch (Exception e) {
            log.warn("FaqClusterGenerationService: question polishing failed for '{}': {}",
                rawQuestion, e.getMessage());
        }
        return rawQuestion;
    }

    // ── JSON helpers ──────────────────────────────────────────────────────────

    private String buildSourcesJson(List<com.docai.bot.domain.model.RetrievedChunk> chunks) {
        if (chunks == null || chunks.isEmpty()) return "[]";
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < chunks.size(); i++) {
            var c = chunks.get(i);
            if (i > 0) sb.append(",");
            sb.append("{\"document\":\"").append(escapeJson(c.getDocumentName()))
              .append("\",\"product\":\"").append(escapeJson(c.getProduct()))
              .append("\",\"version\":\"").append(escapeJson(c.getVersion()))
              .append("\",\"similarity\":").append(String.format("%.3f", c.getSimilarity()))
              .append("}");
        }
        sb.append("]");
        return sb.toString();
    }

    private static String escapeJson(String s) {
        return s == null ? "" : s.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}

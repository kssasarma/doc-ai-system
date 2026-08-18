package com.docai.bot.application.service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import com.docai.bot.config.TenantContext;
import com.docai.bot.domain.entity.QuerySessionGraph;
import com.docai.bot.domain.repository.QuerySessionGraphRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Phase 6.3 — Auto-FAQ Generator.
 *
 * Weekly scheduled job that:
 *  1. Loads all queries from the past 30 days
 *  2. Groups them by tenant + product/version to preserve isolation
 *  3. Clusters semantically using embedding cosine similarity (falls back to Jaccard on error)
 *  4. For each cluster with ≥5 queries, generates a canonical Q&A via {@link FaqClusterGenerationService}
 *  5. Saves PENDING FAQ entries for admin review
 *
 * Per-cluster persistence is delegated to {@link FaqClusterGenerationService} (a separate bean)
 * so each cluster's transaction is independent — one failure cannot roll back another cluster's
 * successfully committed entry (calling {@code this.method()} would bypass Spring's proxy and
 * make {@code @Transactional} a no-op).
 *
 * TenantContext management: the scheduler thread has no tenant context. For each per-tenant
 * block we save/restore whatever was there before (none for the cron path; a real tenant for the
 * admin-triggered path) following the same pattern as {@link DocumentationGapService}.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AutoFaqService {

    private static final int MIN_CLUSTER_SIZE = 5;
    /**
     * Cosine similarity threshold for semantic clustering. 0.75 captures paraphrases reliably
     * (two questions on the same topic typically score 0.75–0.95) while avoiding over-merging
     * tangentially related questions (0.60–0.70 range).
     */
    private static final double SEMANTIC_CLUSTER_THRESHOLD = 0.75;
    /** Jaccard threshold kept identical to the original — used only when embedding fails. */
    private static final double JACCARD_CLUSTER_THRESHOLD = 0.25;
    private static final int LOOKBACK_DAYS = 30;

    private final QuerySessionGraphRepository querySessionGraphRepository;
    private final FaqClusterGenerationService faqClusterGenerationService;
    private final LLMRouter llmRouter;

    /** Runs every Sunday at 02:00 UTC. */
    @Scheduled(cron = "0 0 2 * * SUN")
    public void generateWeeklyFaq() {
        log.info("AutoFaqService: starting weekly FAQ generation job");
        LocalDate periodEnd = LocalDate.now();
        LocalDate periodStart = periodEnd.minusDays(LOOKBACK_DAYS);

        List<QuerySessionGraph> recent = querySessionGraphRepository
            .findAllSince(LocalDateTime.now().minusDays(LOOKBACK_DAYS));

        if (recent.size() < MIN_CLUSTER_SIZE) {
            log.info("AutoFaqService: only {} queries in period, skipping", recent.size());
            return;
        }

        log.info("AutoFaqService: clustering {} queries across all tenants", recent.size());

        // Partition by tenant + product/version before clustering: keeps clusters semantically
        // coherent and guarantees zero cross-tenant blending.
        Map<String, List<QuerySessionGraph>> byTenantAndProduct = new LinkedHashMap<>();
        for (QuerySessionGraph q : recent) {
            String key = q.getTenantId() + "||" + q.getProduct() + "||" + q.getVersion();
            byTenantAndProduct.computeIfAbsent(key, k -> new ArrayList<>()).add(q);
        }

        int totalGenerated = 0;
        for (Map.Entry<String, List<QuerySessionGraph>> entry : byTenantAndProduct.entrySet()) {
            String[] parts = entry.getKey().split("\\|\\|", 3);
            UUID tenantId = UUID.fromString(parts[0]);
            String product = "null".equals(parts[1]) ? null : parts[1];
            String version = parts.length > 2 && !"null".equals(parts[2]) ? parts[2] : null;

            List<QuerySessionGraph> queries = entry.getValue();

            // Set TenantContext for LLM calls (embedding + answer generation) made within this
            // per-tenant block. Save/restore so the cron path (no prior context) and the
            // admin-trigger path (context already set) both work correctly.
            UUID previousTenant = TenantContext.getOrNull();
            TenantContext.set(tenantId);
            try {
                ClusterResult clustered = clusterWithEmbeddings(queries);
                for (List<Integer> indices : clustered.clusters()) {
                    if (indices.size() < MIN_CLUSTER_SIZE) continue;
                    List<QuerySessionGraph> cluster = indices.stream().map(queries::get).toList();
                    List<float[]> embeddings = clustered.embeddings() != null
                        ? indices.stream().map(i -> clustered.embeddings()[i]).toList()
                        : List.of();
                    try {
                        totalGenerated += faqClusterGenerationService.generateFaqForCluster(
                            cluster, embeddings, tenantId, product, version, periodStart, periodEnd);
                    } catch (Exception e) {
                        log.error("AutoFaqService: failed for cluster in tenant {} ({} queries): {}",
                            tenantId, cluster.size(), e.getMessage(), e);
                    }
                }
            } finally {
                if (previousTenant != null) TenantContext.set(previousTenant);
                else TenantContext.clear();
            }
        }

        log.info("AutoFaqService: generated {} new FAQ entries pending review", totalGenerated);
    }

    /**
     * Allows admins to trigger FAQ generation on demand for a specific product within their own
     * tenant. TenantContext is already set by the request thread on this path.
     */
    public int generateForProduct(UUID tenantId, String product, String version) {
        LocalDate periodEnd = LocalDate.now();
        LocalDate periodStart = periodEnd.minusDays(LOOKBACK_DAYS);

        List<QuerySessionGraph> recent = querySessionGraphRepository
            .findRecentQueriesForProduct(tenantId, product, version,
                LocalDateTime.now().minusDays(LOOKBACK_DAYS), UUID.randomUUID());

        ClusterResult clustered = clusterWithEmbeddings(recent);
        int count = 0;
        for (List<Integer> indices : clustered.clusters()) {
            if (indices.size() < MIN_CLUSTER_SIZE) continue;
            List<QuerySessionGraph> cluster = indices.stream().map(recent::get).toList();
            List<float[]> embeddings = clustered.embeddings() != null
                ? indices.stream().map(i -> clustered.embeddings()[i]).toList()
                : List.of();
            count += faqClusterGenerationService.generateFaqForCluster(
                cluster, embeddings, tenantId, product, version, periodStart, periodEnd);
        }
        return count;
    }

    // ── Clustering ───────────────────────────────────────────────────────────

    private record ClusterResult(List<List<Integer>> clusters, float[][] embeddings) {}

    /**
     * Embeds all query texts and clusters by cosine similarity. Falls back to lexical Jaccard
     * clustering when the embedding call fails so a transient LLM outage never skips an entire
     * tenant's generation run — the fallback produces coarser clusters but never nothing.
     */
    private ClusterResult clusterWithEmbeddings(List<QuerySessionGraph> queries) {
        if (queries.isEmpty()) return new ClusterResult(List.of(), null);
        try {
            float[][] embeddings = new float[queries.size()][];
            for (int i = 0; i < queries.size(); i++) {
                List<Double> vec = llmRouter.embed(queries.get(i).getQueryText());
                float[] floats = new float[vec.size()];
                for (int d = 0; d < vec.size(); d++) floats[d] = vec.get(d).floatValue();
                embeddings[i] = floats;
            }
            List<List<Integer>> clusters = SemanticClusterer.cluster(embeddings, SEMANTIC_CLUSTER_THRESHOLD);
            log.debug("AutoFaqService: semantic clustering → {} clusters from {} queries",
                clusters.size(), queries.size());
            return new ClusterResult(clusters, embeddings);
        } catch (Exception e) {
            log.warn("AutoFaqService: embedding failed, falling back to Jaccard clustering: {}", e.getMessage());
            return new ClusterResult(jaccardCluster(queries), null);
        }
    }

    /** Lexical fallback — identical algorithm to the original implementation. */
    private List<List<Integer>> jaccardCluster(List<QuerySessionGraph> queries) {
        boolean[] assigned = new boolean[queries.size()];
        List<List<Integer>> clusters = new ArrayList<>();
        for (int i = 0; i < queries.size(); i++) {
            if (assigned[i]) continue;
            List<Integer> cluster = new ArrayList<>();
            cluster.add(i);
            assigned[i] = true;
            String[] tokI = TextSimilarity.tokenize(queries.get(i).getQueryText());
            for (int j = i + 1; j < queries.size(); j++) {
                if (assigned[j]) continue;
                if (TextSimilarity.jaccardSimilarity(tokI,
                        TextSimilarity.tokenize(queries.get(j).getQueryText())) >= JACCARD_CLUSTER_THRESHOLD) {
                    cluster.add(j);
                    assigned[j] = true;
                }
            }
            clusters.add(cluster);
        }
        return clusters;
    }
}

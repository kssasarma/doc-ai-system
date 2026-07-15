package com.docai.bot.application.service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import com.docai.bot.domain.entity.QuerySessionGraph;
import com.docai.bot.domain.repository.QuerySessionGraphRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Phase 6.3 — Auto-FAQ Generator.
 *
 * Weekly scheduled job that:
 *  1. Loads all queries from the past 30 days
 *  2. Groups them into semantic clusters using Jaccard similarity
 *  3. For each cluster with ≥5 queries, generates a canonical Q&A
 *  4. Saves PENDING FAQ entries for admin review
 *
 * Per-cluster persistence lives in {@link FaqClusterGenerationService} (a separate bean) rather
 * than a {@code @Transactional} method on this class — calling it via the injected bean goes
 * through Spring's real transactional proxy, so one cluster's failure can't take down the
 * transaction boundary of any other cluster in the same run.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AutoFaqService {

    private static final int MIN_CLUSTER_SIZE = 5;
    private static final double CLUSTER_THRESHOLD = 0.25;
    private static final int LOOKBACK_DAYS = 30;

    private final QuerySessionGraphRepository querySessionGraphRepository;
    private final FaqClusterGenerationService faqClusterGenerationService;

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

        log.info("AutoFaqService: clustering {} queries", recent.size());

        // Group by tenant + product/version first to keep clusters semantically coherent AND
        // never blend two tenants' questions into the same generated FAQ entry.
        Map<String, List<QuerySessionGraph>> byTenantAndProduct = new LinkedHashMap<>();
        for (QuerySessionGraph q : recent) {
            String key = q.getTenantId() + "||" + q.getProduct() + "||" + q.getVersion();
            byTenantAndProduct.computeIfAbsent(key, k -> new ArrayList<>()).add(q);
        }

        int totalGenerated = 0;
        for (Map.Entry<String, List<QuerySessionGraph>> entry : byTenantAndProduct.entrySet()) {
            String[] parts = entry.getKey().split("\\|\\|", 3);
            java.util.UUID tenantId = java.util.UUID.fromString(parts[0]);
            String product = "null".equals(parts[1]) ? null : parts[1];
            String version = parts.length > 2 && !"null".equals(parts[2]) ? parts[2] : null;

            List<List<QuerySessionGraph>> clusters = cluster(entry.getValue());
            for (List<QuerySessionGraph> cluster : clusters) {
                if (cluster.size() < MIN_CLUSTER_SIZE) continue;
                try {
                    totalGenerated += faqClusterGenerationService.generateFaqForCluster(
                        cluster, tenantId, product, version, periodStart, periodEnd);
                } catch (Exception e) {
                    log.error("AutoFaqService: failed to generate FAQ for a cluster in tenant {} ({} queries): {}",
                        tenantId, cluster.size(), e.getMessage(), e);
                }
            }
        }

        log.info("AutoFaqService: generated {} new FAQ entries pending review", totalGenerated);
    }

    /** Allows admins to trigger FAQ generation on demand for a specific product, within their own tenant. */
    public int generateForProduct(java.util.UUID tenantId, String product, String version) {
        LocalDate periodEnd = LocalDate.now();
        LocalDate periodStart = periodEnd.minusDays(LOOKBACK_DAYS);

        List<QuerySessionGraph> recent = querySessionGraphRepository
            .findRecentQueriesForProduct(tenantId, product, version,
                LocalDateTime.now().minusDays(LOOKBACK_DAYS), java.util.UUID.randomUUID());

        List<List<QuerySessionGraph>> clusters = cluster(recent);
        int count = 0;
        for (List<QuerySessionGraph> cl : clusters) {
            if (cl.size() < MIN_CLUSTER_SIZE) continue;
            count += faqClusterGenerationService.generateFaqForCluster(cl, tenantId, product, version, periodStart, periodEnd);
        }
        return count;
    }

    // ── Clustering ───────────────────────────────────────────────────────────

    private List<List<QuerySessionGraph>> cluster(List<QuerySessionGraph> queries) {
        List<List<QuerySessionGraph>> clusters = new ArrayList<>();
        boolean[] assigned = new boolean[queries.size()];

        for (int i = 0; i < queries.size(); i++) {
            if (assigned[i]) continue;
            List<QuerySessionGraph> cluster = new ArrayList<>();
            cluster.add(queries.get(i));
            assigned[i] = true;

            String[] tokensI = TextSimilarity.tokenize(queries.get(i).getQueryText());
            for (int j = i + 1; j < queries.size(); j++) {
                if (assigned[j]) continue;
                double sim = TextSimilarity.jaccardSimilarity(tokensI, TextSimilarity.tokenize(queries.get(j).getQueryText()));
                if (sim >= CLUSTER_THRESHOLD) {
                    cluster.add(queries.get(j));
                    assigned[j] = true;
                }
            }
            clusters.add(cluster);
        }
        return clusters;
    }
}

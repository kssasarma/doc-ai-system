package com.docai.bot.application.service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.docai.bot.domain.entity.FaqCluster;
import com.docai.bot.domain.entity.FaqEntry;
import com.docai.bot.domain.entity.FaqEntry.Status;
import com.docai.bot.domain.entity.QuerySessionGraph;
import com.docai.bot.domain.model.SearchScope;
import com.docai.bot.domain.repository.DocumentRepository;
import com.docai.bot.domain.repository.FaqClusterRepository;
import com.docai.bot.domain.repository.FaqEntryRepository;
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
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AutoFaqService {

    private static final int MIN_CLUSTER_SIZE = 5;
    private static final double CLUSTER_THRESHOLD = 0.25;
    private static final int LOOKBACK_DAYS = 30;

    private final QuerySessionGraphRepository querySessionGraphRepository;
    private final FaqClusterRepository faqClusterRepository;
    private final FaqEntryRepository faqEntryRepository;
    private final VectorSearchService vectorSearchService;
    private final AnswerGenerationService answerGenerationService;
    private final DocumentRepository documentRepository;

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
                totalGenerated += generateFaqForCluster(cluster, tenantId, product, version, periodStart, periodEnd);
            }
        }

        log.info("AutoFaqService: generated {} new FAQ entries pending review", totalGenerated);
    }

    /** Allows admins to trigger FAQ generation on demand for a specific product, within their own tenant. */
    @Transactional
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
            count += generateFaqForCluster(cl, tenantId, product, version, periodStart, periodEnd);
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

            String[] tokensI = tokenize(queries.get(i).getQueryText());
            for (int j = i + 1; j < queries.size(); j++) {
                if (assigned[j]) continue;
                double sim = jaccardSimilarity(tokensI, tokenize(queries.get(j).getQueryText()));
                if (sim >= CLUSTER_THRESHOLD) {
                    cluster.add(queries.get(j));
                    assigned[j] = true;
                }
            }
            clusters.add(cluster);
        }
        return clusters;
    }

    // ── FAQ Generation ────────────────────────────────────────────────────────

    @Transactional
    int generateFaqForCluster(List<QuerySessionGraph> cluster, java.util.UUID tenantId, String product, String version,
                               LocalDate periodStart, LocalDate periodEnd) {
        String canonical = pickCanonical(cluster);
        long uniqueUsers = cluster.stream()
            .map(QuerySessionGraph::getUserId).filter(u -> u != null).distinct().count();

        // Avoid regenerating the same cluster in the same period
        boolean alreadyExists = faqClusterRepository
            .findByTenantIdAndProductAndVersionAndPeriodStartAndPeriodEnd(tenantId, product, version, periodStart, periodEnd)
            .stream()
            .anyMatch(fc -> jaccardSimilarity(tokenize(fc.getCanonicalQuestion()),
                                              tokenize(canonical)) > 0.6);
        if (alreadyExists) return 0;

        FaqCluster faqCluster = FaqCluster.builder()
            .tenantId(tenantId)
            .product(product)
            .version(version)
            .canonicalQuestion(canonical)
            .queryCount(cluster.size())
            .uniqueUsers((int) uniqueUsers)
            .periodStart(periodStart)
            .periodEnd(periodEnd)
            .build();
        faqCluster = faqClusterRepository.save(faqCluster);

        // Tenant-scoped: an admin's implicit full-corpus visibility, same as chat retrieval for
        // an ADMIN caller (see GrantBasedDocumentAccessPolicy) — never the deprecated
        // all-tenants search, since the generated answer becomes tenant-facing content.
        SearchScope scope = new SearchScope(tenantId, documentRepository.findIdsByTenantId(tenantId))
            .withVersionNarrow(product, version);
        var chunks = vectorSearchService.search(canonical, scope);
        AnswerGenerationService.AnswerResult result =
            answerGenerationService.generateAnswer(canonical, null, chunks, "BALANCED", "PROSE", product, version);

        String sourcesJson = buildSourcesJson(chunks);

        FaqEntry entry = FaqEntry.builder()
            .clusterId(faqCluster.getId())
            .tenantId(tenantId)
            .question(canonical)
            .answer(result.answer())
            .product(product)
            .version(version)
            .sources(sourcesJson)
            .status(Status.PENDING)
            .build();
        faqEntryRepository.save(entry);

        log.info("Generated FAQ entry for: '{}' ({} queries, {} users)", canonical, cluster.size(), uniqueUsers);
        return 1;
    }

    private String pickCanonical(List<QuerySessionGraph> cluster) {
        // Most representative = highest average Jaccard similarity to all others
        String[] texts = cluster.stream()
            .map(QuerySessionGraph::getQueryText).toArray(String[]::new);

        double bestScore = -1;
        String best = texts[0];
        for (String candidate : texts) {
            String[] cTok = tokenize(candidate);
            double avgSim = 0;
            for (String other : texts) {
                avgSim += jaccardSimilarity(cTok, tokenize(other));
            }
            avgSim /= texts.length;
            if (avgSim > bestScore) {
                bestScore = avgSim;
                best = candidate;
            }
        }
        return best;
    }

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

    private static String[] tokenize(String text) {
        if (text == null) return new String[0];
        return text.toLowerCase().replaceAll("[^a-z0-9\\s]", " ").split("\\s+");
    }

    private static double jaccardSimilarity(String[] a, String[] b) {
        var setA = java.util.Set.of(a);
        var setB = java.util.Set.of(b);
        long intersection = setA.stream().filter(setB::contains).count();
        long union = setA.size() + setB.size() - intersection;
        return union == 0 ? 0.0 : (double) intersection / union;
    }
}

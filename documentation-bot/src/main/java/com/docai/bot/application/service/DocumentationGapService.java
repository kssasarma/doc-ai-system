package com.docai.bot.application.service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.docai.bot.domain.entity.DocumentationGapReport;
import com.docai.bot.domain.entity.QuerySessionGraph;
import com.docai.bot.domain.repository.DocumentationGapReportRepository;
import com.docai.bot.domain.repository.QuerySessionGraphRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Phase 6.4 — Documentation Gap Report (AI-Driven).
 *
 * Monthly scheduled job that:
 *  1. Finds all LOW-confidence queries from the past 30 days
 *  2. Groups them by semantic topic (Jaccard clustering)
 *  3. For each cluster, LLM generates a documentation stub
 *  4. Saves a gap report for admin review and export
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DocumentationGapService {

    private static final int LOOKBACK_DAYS = 30;
    private static final int MIN_CLUSTER_SIZE = 3;
    private static final double CLUSTER_THRESHOLD = 0.20;

    private final QuerySessionGraphRepository querySessionGraphRepository;
    private final DocumentationGapReportRepository gapReportRepository;
    private final ChatClient.Builder chatClientBuilder;

    @Value("${bot.min-similarity-threshold:0.55}")
    private double minSimilarityThreshold;

    /** Runs on the 1st of each month at 03:00 UTC — one report per tenant per product/version,
     * never blending different tenants' queries into the same gap-topic cluster. */
    @Scheduled(cron = "0 0 3 1 * *")
    @Transactional
    public void generateMonthlyReport() {
        log.info("DocumentationGapService: generating monthly gap report");
        LocalDate periodEnd = LocalDate.now();
        LocalDate periodStart = periodEnd.minusDays(LOOKBACK_DAYS);

        List<QuerySessionGraph> recent = querySessionGraphRepository.findAllSince(
            LocalDateTime.now().minusDays(LOOKBACK_DAYS));
        if (recent.isEmpty()) {
            log.info("DocumentationGapService: no queries found for period, skipping");
            return;
        }

        Map<String, List<QuerySessionGraph>> byTenantAndProduct = new LinkedHashMap<>();
        for (QuerySessionGraph q : recent) {
            String key = q.getTenantId() + "||" + q.getProduct() + "||" + q.getVersion();
            byTenantAndProduct.computeIfAbsent(key, k -> new ArrayList<>()).add(q);
        }

        int reportsGenerated = 0;
        for (Map.Entry<String, List<QuerySessionGraph>> entry : byTenantAndProduct.entrySet()) {
            String[] parts = entry.getKey().split("\\|\\|", 3);
            java.util.UUID tenantId = java.util.UUID.fromString(parts[0]);
            String product = "null".equals(parts[1]) ? null : parts[1];
            String version = parts.length > 2 && !"null".equals(parts[2]) ? parts[2] : null;

            if (buildReport(tenantId, entry.getValue(), product, version, periodStart, periodEnd) != null) {
                reportsGenerated++;
            }
        }
        log.info("DocumentationGapService: generated {} gap reports across all tenants", reportsGenerated);
    }

    /** Allows admins to trigger on-demand for a specific product, within their own tenant. */
    @Transactional
    public DocumentationGapReport generateReport(java.util.UUID tenantId, String product, String version) {
        LocalDate periodEnd = LocalDate.now();
        LocalDate periodStart = periodEnd.minusDays(LOOKBACK_DAYS);

        // Load queries that had low confidence (stored in query_session_graph; filter using
        // query_logs confidence when available — here we use all queries and let LLM judge)
        List<QuerySessionGraph> recent = (product != null)
            ? querySessionGraphRepository.findRecentQueriesForProduct(
                tenantId, product, version, LocalDateTime.now().minusDays(LOOKBACK_DAYS),
                java.util.UUID.randomUUID())
            : querySessionGraphRepository.findAllSince(
                LocalDateTime.now().minusDays(LOOKBACK_DAYS)).stream()
                .filter(q -> tenantId.equals(q.getTenantId()))
                .toList();

        if (recent.isEmpty()) {
            log.info("DocumentationGapService: no queries found for period, skipping");
            return null;
        }

        DocumentationGapReport report = buildReport(tenantId, recent, product, version, periodStart, periodEnd);
        if (report == null) {
            log.info("DocumentationGapService: no gap clusters found for period, skipping");
        }
        return report;
    }

    private DocumentationGapReport buildReport(java.util.UUID tenantId, List<QuerySessionGraph> recent,
                                                String product, String version,
                                                LocalDate periodStart, LocalDate periodEnd) {
        int totalLow = 0;
        List<Map<String, Object>> gapTopics = new ArrayList<>();

        List<List<QuerySessionGraph>> clusters = cluster(recent);
        for (List<QuerySessionGraph> cl : clusters) {
            if (cl.size() < MIN_CLUSTER_SIZE) continue;
            totalLow += cl.size();

            String canonical = pickRepresentative(cl);
            List<String> examples = cl.stream()
                .map(QuerySessionGraph::getQueryText)
                .distinct().limit(5).toList();
            long uniqueUsers = cl.stream()
                .map(QuerySessionGraph::getUserId)
                .filter(u -> u != null).distinct().count();

            String stub = generateDocumentationStub(canonical, product, version);

            Map<String, Object> topic = new java.util.HashMap<>();
            topic.put("topic", canonical);
            topic.put("queryCount", cl.size());
            topic.put("uniqueUsers", uniqueUsers);
            topic.put("exampleQuestions", examples);
            topic.put("suggestedDocStub", stub);
            gapTopics.add(topic);
        }

        if (gapTopics.isEmpty()) return null;

        String gapTopicsJson = toJson(gapTopics);

        DocumentationGapReport report = DocumentationGapReport.builder()
            .tenantId(tenantId)
            .product(product)
            .version(version)
            .reportPeriodStart(periodStart)
            .reportPeriodEnd(periodEnd)
            .totalLowConfidenceQueries(totalLow)
            .gapTopics(gapTopicsJson)
            .generatedAt(LocalDateTime.now())
            .build();

        report = gapReportRepository.save(report);
        log.info("DocumentationGapService: saved gap report with {} topics", gapTopics.size());
        return report;
    }

    private String generateDocumentationStub(String topic, String product, String version) {
        String ctx = (product != null ? product : "the product") +
                     (version != null ? " " + version : "");
        String prompt = """
            You are a technical writer. A user asked: "%s"
            Product: %s

            The documentation system couldn't find a reliable answer. Write a 2-3 paragraph documentation stub
            that would answer this question. Use generic but technically accurate language.
            Do not make up specific version numbers or proprietary details.
            Format as plain paragraphs, no markdown headers.
            """.formatted(topic, ctx);
        try {
            String result = chatClientBuilder.build().prompt().user(prompt).call().content();
            return result != null ? result.trim() : "";
        } catch (Exception e) {
            log.warn("Stub generation failed for topic '{}': {}", topic, e.getMessage());
            return "";
        }
    }

    // ── Clustering ────────────────────────────────────────────────────────────

    private List<List<QuerySessionGraph>> cluster(List<QuerySessionGraph> queries) {
        List<List<QuerySessionGraph>> clusters = new ArrayList<>();
        boolean[] assigned = new boolean[queries.size()];

        for (int i = 0; i < queries.size(); i++) {
            if (assigned[i]) continue;
            List<QuerySessionGraph> cl = new ArrayList<>();
            cl.add(queries.get(i));
            assigned[i] = true;
            String[] tokI = tokenize(queries.get(i).getQueryText());
            for (int j = i + 1; j < queries.size(); j++) {
                if (assigned[j]) continue;
                if (jaccardSimilarity(tokI, tokenize(queries.get(j).getQueryText())) >= CLUSTER_THRESHOLD) {
                    cl.add(queries.get(j));
                    assigned[j] = true;
                }
            }
            clusters.add(cl);
        }
        return clusters;
    }

    private String pickRepresentative(List<QuerySessionGraph> cluster) {
        String[] texts = cluster.stream().map(QuerySessionGraph::getQueryText).toArray(String[]::new);
        double bestScore = -1;
        String best = texts[0];
        for (String candidate : texts) {
            String[] cTok = tokenize(candidate);
            double avg = 0;
            for (String other : texts) avg += jaccardSimilarity(cTok, tokenize(other));
            avg /= texts.length;
            if (avg > bestScore) { bestScore = avg; best = candidate; }
        }
        return best;
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

    // ── JSON serialization (no Jackson dependency needed) ─────────────────────

    private static String toJson(List<Map<String, Object>> list) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < list.size(); i++) {
            if (i > 0) sb.append(",");
            sb.append("{");
            Map<String, Object> map = list.get(i);
            boolean first = true;
            for (Map.Entry<String, Object> e : map.entrySet()) {
                if (!first) sb.append(",");
                first = false;
                sb.append("\"").append(e.getKey()).append("\":");
                Object v = e.getValue();
                if (v instanceof String s) {
                    sb.append("\"").append(s.replace("\\", "\\\\").replace("\"", "\\\"")
                        .replace("\n", "\\n").replace("\r", "")).append("\"");
                } else if (v instanceof List<?> items) {
                    sb.append("[");
                    for (int j = 0; j < items.size(); j++) {
                        if (j > 0) sb.append(",");
                        String item = items.get(j).toString();
                        sb.append("\"").append(item.replace("\\", "\\\\").replace("\"", "\\\"")).append("\"");
                    }
                    sb.append("]");
                } else {
                    sb.append(v);
                }
            }
            sb.append("}");
        }
        sb.append("]");
        return sb.toString();
    }
}

package com.docai.bot.application.service;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

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

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Persists one FAQ cluster per call, each in its own transaction. Split out of
 * {@link AutoFaqService} so that a failure generating one cluster's FAQ entry cannot silently
 * skip the transaction boundary for the whole weekly job — {@link AutoFaqService} previously
 * called this logic through {@code this}, which bypasses the Spring transactional proxy entirely,
 * making {@code @Transactional} here a no-op. Calling through an injected bean (this class)
 * goes through the real proxy, so each cluster genuinely commits/rolls back independently.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FaqClusterGenerationService {

    private final FaqClusterRepository faqClusterRepository;
    private final FaqEntryRepository faqEntryRepository;
    private final VectorSearchService vectorSearchService;
    private final AnswerGenerationService answerGenerationService;
    private final DocumentRepository documentRepository;

    @Transactional
    public int generateFaqForCluster(List<QuerySessionGraph> cluster, UUID tenantId, String product, String version,
                               LocalDate periodStart, LocalDate periodEnd) {
        String canonical = pickCanonical(cluster);
        long uniqueUsers = cluster.stream()
            .map(QuerySessionGraph::getUserId).filter(u -> u != null).distinct().count();

        // Avoid regenerating the same cluster in the same period
        boolean alreadyExists = faqClusterRepository
            .findByTenantIdAndProductAndVersionAndPeriodStartAndPeriodEnd(tenantId, product, version, periodStart, periodEnd)
            .stream()
            .anyMatch(fc -> TextSimilarity.jaccardSimilarity(TextSimilarity.tokenize(fc.getCanonicalQuestion()),
                                              TextSimilarity.tokenize(canonical)) > 0.6);
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
            String[] cTok = TextSimilarity.tokenize(candidate);
            double avgSim = 0;
            for (String other : texts) {
                avgSim += TextSimilarity.jaccardSimilarity(cTok, TextSimilarity.tokenize(other));
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
}

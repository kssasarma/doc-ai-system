package com.docai.bot.application.service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import com.docai.bot.application.event.ChatQueryRecordedEvent;
import com.docai.bot.domain.entity.QueryLog;
import com.docai.bot.domain.entity.User;
import com.docai.bot.domain.repository.AnswerFeedbackRepository;
import com.docai.bot.domain.repository.ChatSessionRepository;
import com.docai.bot.domain.repository.QueryLogRepository;
import com.docai.bot.domain.repository.UserRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class AnalyticsService {

    private final QueryLogRepository queryLogRepository;
    private final UserRepository userRepository;
    private final AnswerFeedbackRepository feedbackRepository;
    private final ChatSessionRepository sessionRepository;

    // ── Query logging ────────────────────────────────────────────────────────
    // Async AND gated on the publishing transaction having committed — the session this query
    // log's foreign key points at may have been created in that same transaction, so running
    // this any earlier (as a plain @Async method called mid-transaction, which is what this used
    // to be) could race the session's own INSERT and intermittently fail query_logs_session_id_fkey.

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional
    public void onQueryRecorded(ChatQueryRecordedEvent event) {
        try {
            QueryLog entry = QueryLog.builder()
                .userId(event.userId())
                .tenantId(event.tenantId())
                .sessionId(event.sessionId())
                .questionPreview(event.question() != null
                    ? event.question().substring(0, Math.min(200, event.question().length())) : null)
                .product(event.product())
                .version(event.version())
                .confidence(event.confidence())
                .latencyMs((int) Math.min(event.latencyMs(), Integer.MAX_VALUE))
                .promptTokens(event.promptTokens())
                .completionTokens(event.completionTokens())
                .estimatedCostUsd(event.estimatedCostUsd())
                .citedDocuments(event.citedDocuments())
                .build();
            queryLogRepository.save(entry);
        } catch (Exception e) {
            log.warn("Failed to save query log: {}", e.getMessage());
        }
    }

    // ── Overview ──────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public OverviewDTO getOverview(UUID tenantId) {
        LocalDateTime dayAgo   = LocalDateTime.now().minusDays(1);
        LocalDateTime weekAgo  = LocalDateTime.now().minusDays(7);
        LocalDateTime monthAgo = LocalDateTime.now().minusDays(30);

        long totalAll      = queryLogRepository.countByTenantId(tenantId);
        long today         = queryLogRepository.countByTenantIdAndCreatedAtAfter(tenantId, dayAgo);
        long thisWeek      = queryLogRepository.countByTenantIdAndCreatedAtAfter(tenantId, weekAgo);
        long thisMonth     = queryLogRepository.countByTenantIdAndCreatedAtAfter(tenantId, monthAgo);
        long dau           = queryLogRepository.countDistinctUsersSince(tenantId, dayAgo);
        long wau           = queryLogRepository.countDistinctUsersSince(tenantId, weekAgo);
        long mau           = queryLogRepository.countDistinctUsersSince(tenantId, monthAgo);

        double avgSessionLength = sessionRepository.findByTenantIdOrderByPinnedDescLastActiveAtDesc(tenantId).stream()
            .mapToInt(s -> s.getMessageCount() != null ? s.getMessageCount() : 0)
            .average().orElse(0.0);

        long positive = feedbackRepository.countByTenantIdAndRating(tenantId, (short) 1);
        long negative = feedbackRepository.countByTenantIdAndRating(tenantId, (short) -1);

        Double avgConf = queryLogRepository.avgConfidenceSince(tenantId, monthAgo);

        return OverviewDTO.builder()
            .totalQueriesAllTime(totalAll)
            .queriesToday(today)
            .queriesThisWeek(thisWeek)
            .queriesThisMonth(thisMonth)
            .dauToday(dau)
            .wauThisWeek(wau)
            .mauThisMonth(mau)
            .avgSessionLength(avgSessionLength)
            .totalPositiveFeedback(positive)
            .totalNegativeFeedback(negative)
            .avgConfidence(avgConf != null ? avgConf : 0.0)
            .build();
    }

    // ── Daily stats (queries/day + cost trend) ─────────────────────────────

    @Transactional(readOnly = true)
    public List<DailyStatDTO> getDailyStats(UUID tenantId, int days) {
        LocalDateTime since = LocalDateTime.now().minusDays(days);
        return queryLogRepository.getDailyStats(tenantId, since).stream()
            .map(row -> DailyStatDTO.builder()
                .date(str(row[0]))
                .queryCount(toLong(row[1]))
                .avgConfidence(toDouble(row[2]))
                .estimatedCost(toDouble(row[3]))
                .build())
            .collect(Collectors.toList());
    }

    // ── Top questions ─────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<TopQuestionDTO> getTopQuestions(UUID tenantId, int limit, int days) {
        LocalDateTime since = LocalDateTime.now().minusDays(days);
        return queryLogRepository.findTopQuestions(tenantId, since, PageRequest.of(0, limit)).stream()
            .map(row -> TopQuestionDTO.builder()
                .questionPreview(str(row[0]))
                .count(toLong(row[1]))
                .product(str(row[2]))
                .version(str(row[3]))
                .build())
            .collect(Collectors.toList());
    }

    // ── Product coverage ──────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<ProductCoverageDTO> getProductCoverage(UUID tenantId) {
        return queryLogRepository.getProductCoverageStats(tenantId).stream()
            .map(row -> {
                long queryCount    = toLong(row[2]);
                double avgConf     = toDouble(row[3]);
                long lowConfCount  = toLong(row[4]);
                double lowConfPct  = queryCount > 0 ? (lowConfCount * 100.0 / queryCount) : 0.0;
                return ProductCoverageDTO.builder()
                    .product(str(row[0]))
                    .version(str(row[1]))
                    .queryCount(queryCount)
                    .avgConfidence(avgConf)
                    .lowConfidenceCount(lowConfCount)
                    .lowConfidencePct(lowConfPct)
                    .build();
            })
            .collect(Collectors.toList());
    }

    // ── User engagement ───────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<UserEngagementDTO> getUserEngagement(UUID tenantId) {
        // Build userId→username map, scoped to this tenant's own users
        Map<String, String> usernameMap = userRepository.findByTenantId(tenantId).stream()
            .collect(Collectors.toMap(u -> u.getId().toString(), User::getUsername));

        return queryLogRepository.getUserEngagementStats(tenantId).stream()
            .map(row -> {
                String uid = str(row[0]);
                return UserEngagementDTO.builder()
                    .userId(uid)
                    .username(usernameMap.getOrDefault(uid, "unknown"))
                    .queryCount(toLong(row[1]))
                    .avgConfidence(toDouble(row[2]))
                    .lastActive(str(row[3]))
                    .build();
            })
            .collect(Collectors.toList());
    }

    // ── Cost summary ──────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public CostSummaryDTO getCostSummary(UUID tenantId, int days) {
        LocalDateTime since = LocalDateTime.now().minusDays(days);
        Map<String, String> usernameMap = userRepository.findByTenantId(tenantId).stream()
            .collect(Collectors.toMap(u -> u.getId().toString(), User::getUsername));

        double totalThisMonth = queryLogRepository.sumCostSince(tenantId, since);
        double totalAllTime   = queryLogRepository.sumCostAllTime(tenantId);
        long   queryCountMonth = queryLogRepository.countByTenantIdAndCreatedAtAfter(tenantId, since);
        double avgCost = queryCountMonth > 0 ? totalThisMonth / queryCountMonth : 0.0;

        List<DailyStatDTO> daily = getDailyStats(tenantId, days);

        List<UserCostDTO> costByUser = queryLogRepository
            .getCostByUser(tenantId, since, PageRequest.of(0, 10)).stream()
            .map(row -> {
                String uid = str(row[0]);
                return UserCostDTO.builder()
                    .userId(uid)
                    .username(usernameMap.getOrDefault(uid, "unknown"))
                    .totalCost(toDouble(row[1]))
                    .queryCount(toLong(row[2]))
                    .build();
            })
            .collect(Collectors.toList());

        List<ProductCostDTO> costByProduct = queryLogRepository.getCostByProduct(tenantId, since).stream()
            .map(row -> ProductCostDTO.builder()
                .product(str(row[0]))
                .version(str(row[1]))
                .totalCost(toDouble(row[2]))
                .queryCount(toLong(row[3]))
                .build())
            .collect(Collectors.toList());

        return CostSummaryDTO.builder()
            .totalCostThisMonth(totalThisMonth)
            .totalCostAllTime(totalAllTime)
            .avgCostPerQuery(avgCost)
            .dailyCost(daily)
            .costByUser(costByUser)
            .costByProduct(costByProduct)
            .build();
    }

    // ── Document coverage ─────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<DocumentCoverageDTO> getDocumentCoverage(UUID tenantId) {
        LocalDateTime since = LocalDateTime.now().minusDays(30);
        return queryLogRepository.getDocumentCitations(tenantId, since).stream()
            .map(row -> DocumentCoverageDTO.builder()
                .documentName(str(row[0]))
                .citationCount(toLong(row[1]))
                .product(str(row[2]))
                .version(str(row[3]))
                .build())
            .collect(Collectors.toList());
    }

    // ── Failed queries ────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<FailedQueryDTO> getFailedQueries(UUID tenantId, int limit) {
        LocalDateTime since = LocalDateTime.now().minusDays(30);
        return queryLogRepository.findFailedQueries(tenantId, since, PageRequest.of(0, limit)).stream()
            .map(row -> FailedQueryDTO.builder()
                .questionPreview(str(row[0]))
                .count(toLong(row[1]))
                .product(str(row[2]))
                .version(str(row[3]))
                .build())
            .collect(Collectors.toList());
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private static String str(Object o) {
        return o != null ? o.toString() : null;
    }

    private static long toLong(Object o) {
        if (o == null) return 0;
        if (o instanceof Number n) return n.longValue();
        try { return Long.parseLong(o.toString()); } catch (NumberFormatException e) { return 0; }
    }

    private static double toDouble(Object o) {
        if (o == null) return 0.0;
        if (o instanceof Number n) return n.doubleValue();
        try { return Double.parseDouble(o.toString()); } catch (NumberFormatException e) { return 0.0; }
    }

    // ── DTOs ──────────────────────────────────────────────────────────────────

    @lombok.Data @lombok.Builder
    public static class OverviewDTO {
        private long totalQueriesAllTime;
        private long queriesToday;
        private long queriesThisWeek;
        private long queriesThisMonth;
        private long dauToday;
        private long wauThisWeek;
        private long mauThisMonth;
        private double avgSessionLength;
        private long totalPositiveFeedback;
        private long totalNegativeFeedback;
        private double avgConfidence;
    }

    @lombok.Data @lombok.Builder
    public static class DailyStatDTO {
        private String date;
        private long queryCount;
        private double avgConfidence;
        private double estimatedCost;
    }

    @lombok.Data @lombok.Builder
    public static class TopQuestionDTO {
        private String questionPreview;
        private long count;
        private String product;
        private String version;
    }

    @lombok.Data @lombok.Builder
    public static class ProductCoverageDTO {
        private String product;
        private String version;
        private long queryCount;
        private double avgConfidence;
        private long lowConfidenceCount;
        private double lowConfidencePct;
    }

    @lombok.Data @lombok.Builder
    public static class UserEngagementDTO {
        private String userId;
        private String username;
        private long queryCount;
        private double avgConfidence;
        private String lastActive;
    }

    @lombok.Data @lombok.Builder
    public static class CostSummaryDTO {
        private double totalCostThisMonth;
        private double totalCostAllTime;
        private double avgCostPerQuery;
        private List<DailyStatDTO> dailyCost;
        private List<UserCostDTO> costByUser;
        private List<ProductCostDTO> costByProduct;
    }

    @lombok.Data @lombok.Builder
    public static class UserCostDTO {
        private String userId;
        private String username;
        private double totalCost;
        private long queryCount;
    }

    @lombok.Data @lombok.Builder
    public static class ProductCostDTO {
        private String product;
        private String version;
        private double totalCost;
        private long queryCount;
    }

    @lombok.Data @lombok.Builder
    public static class DocumentCoverageDTO {
        private String documentName;
        private String product;
        private String version;
        private long citationCount;
    }

    @lombok.Data @lombok.Builder
    public static class FailedQueryDTO {
        private String questionPreview;
        private long count;
        private String product;
        private String version;
    }
}

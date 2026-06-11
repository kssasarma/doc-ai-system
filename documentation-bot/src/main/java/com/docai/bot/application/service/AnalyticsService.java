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

    // ── Query logging (called async from ChatService) ─────────────────────────

    @Async
    @Transactional
    public void logQuery(UUID userId, UUID sessionId, String question,
                         String product, String version, double confidence,
                         long latencyMs, int promptTokens, int completionTokens,
                         String[] citedDocuments, double estimatedCostUsd) {
        try {
            QueryLog entry = QueryLog.builder()
                .userId(userId)
                .sessionId(sessionId)
                .questionPreview(question != null
                    ? question.substring(0, Math.min(200, question.length())) : null)
                .product(product)
                .version(version)
                .confidence(confidence)
                .latencyMs((int) Math.min(latencyMs, Integer.MAX_VALUE))
                .promptTokens(promptTokens)
                .completionTokens(completionTokens)
                .estimatedCostUsd(estimatedCostUsd)
                .citedDocuments(citedDocuments)
                .build();
            queryLogRepository.save(entry);
        } catch (Exception e) {
            log.warn("Failed to save query log: {}", e.getMessage());
        }
    }

    // ── Overview ──────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public OverviewDTO getOverview() {
        LocalDateTime dayAgo   = LocalDateTime.now().minusDays(1);
        LocalDateTime weekAgo  = LocalDateTime.now().minusDays(7);
        LocalDateTime monthAgo = LocalDateTime.now().minusDays(30);

        long totalAll      = queryLogRepository.count();
        long today         = queryLogRepository.countByCreatedAtAfter(dayAgo);
        long thisWeek      = queryLogRepository.countByCreatedAtAfter(weekAgo);
        long thisMonth     = queryLogRepository.countByCreatedAtAfter(monthAgo);
        long dau           = queryLogRepository.countDistinctUsersSince(dayAgo);
        long wau           = queryLogRepository.countDistinctUsersSince(weekAgo);
        long mau           = queryLogRepository.countDistinctUsersSince(monthAgo);

        double avgSessionLength = sessionRepository.findAll().stream()
            .mapToInt(s -> s.getMessageCount() != null ? s.getMessageCount() : 0)
            .average().orElse(0.0);

        long positive = feedbackRepository.countByRating((short) 1);
        long negative = feedbackRepository.countByRating((short) -1);

        Double avgConf = queryLogRepository.avgConfidenceSince(monthAgo);

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
    public List<DailyStatDTO> getDailyStats(int days) {
        LocalDateTime since = LocalDateTime.now().minusDays(days);
        return queryLogRepository.getDailyStats(since).stream()
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
    public List<TopQuestionDTO> getTopQuestions(int limit) {
        LocalDateTime since = LocalDateTime.now().minusDays(30);
        return queryLogRepository.findTopQuestions(since, PageRequest.of(0, limit)).stream()
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
    public List<ProductCoverageDTO> getProductCoverage() {
        return queryLogRepository.getProductCoverageStats().stream()
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
    public List<UserEngagementDTO> getUserEngagement() {
        // Build userId→username map
        Map<String, String> usernameMap = userRepository.findAll().stream()
            .collect(Collectors.toMap(u -> u.getId().toString(), User::getUsername));

        return queryLogRepository.getUserEngagementStats().stream()
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
    public CostSummaryDTO getCostSummary() {
        LocalDateTime monthAgo = LocalDateTime.now().minusDays(30);
        Map<String, String> usernameMap = userRepository.findAll().stream()
            .collect(Collectors.toMap(u -> u.getId().toString(), User::getUsername));

        double totalThisMonth = queryLogRepository.sumCostSince(monthAgo);
        double totalAllTime   = queryLogRepository.sumCostAllTime();
        long   queryCountMonth = queryLogRepository.countByCreatedAtAfter(monthAgo);
        double avgCost = queryCountMonth > 0 ? totalThisMonth / queryCountMonth : 0.0;

        List<DailyStatDTO> daily = getDailyStats(30);

        List<UserCostDTO> costByUser = queryLogRepository
            .getCostByUser(monthAgo, PageRequest.of(0, 10)).stream()
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

        List<ProductCostDTO> costByProduct = queryLogRepository.getCostByProduct(monthAgo).stream()
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
    public List<DocumentCoverageDTO> getDocumentCoverage() {
        LocalDateTime since = LocalDateTime.now().minusDays(30);
        return queryLogRepository.getDocumentCitations(since).stream()
            .map(row -> DocumentCoverageDTO.builder()
                .documentName(str(row[0]))
                .citationCount(toLong(row[1]))
                .build())
            .collect(Collectors.toList());
    }

    // ── Failed queries ────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<FailedQueryDTO> getFailedQueries(int limit) {
        LocalDateTime since = LocalDateTime.now().minusDays(30);
        return queryLogRepository.findFailedQueries(since, PageRequest.of(0, limit)).stream()
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

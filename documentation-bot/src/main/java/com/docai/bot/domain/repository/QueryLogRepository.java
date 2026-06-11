package com.docai.bot.domain.repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.docai.bot.domain.entity.QueryLog;

@Repository
public interface QueryLogRepository extends JpaRepository<QueryLog, UUID> {

    long countByCreatedAtAfter(LocalDateTime since);

    @Query(value = "SELECT COUNT(DISTINCT user_id) FROM query_logs WHERE created_at >= :since", nativeQuery = true)
    long countDistinctUsersSince(@Param("since") LocalDateTime since);

    @Query(value = """
        SELECT DATE(created_at)::TEXT AS day, COUNT(*) AS cnt,
               AVG(confidence) AS avg_conf,
               COALESCE(SUM(estimated_cost_usd), 0) AS daily_cost
        FROM query_logs
        WHERE created_at >= :since
        GROUP BY DATE(created_at)
        ORDER BY DATE(created_at)
        """, nativeQuery = true)
    List<Object[]> getDailyStats(@Param("since") LocalDateTime since);

    @Query(value = """
        SELECT question_preview, COUNT(*) AS cnt, product, version
        FROM query_logs
        WHERE created_at >= :since AND question_preview IS NOT NULL
        GROUP BY question_preview, product, version
        ORDER BY cnt DESC
        """, nativeQuery = true)
    List<Object[]> findTopQuestions(@Param("since") LocalDateTime since, Pageable pageable);

    @Query(value = """
        SELECT product, version,
               COUNT(*) AS query_count,
               AVG(confidence) AS avg_conf,
               SUM(CASE WHEN confidence < 0.6 THEN 1 ELSE 0 END) AS low_conf_count
        FROM query_logs
        WHERE product IS NOT NULL
        GROUP BY product, version
        ORDER BY query_count DESC
        """, nativeQuery = true)
    List<Object[]> getProductCoverageStats();

    @Query(value = """
        SELECT user_id::TEXT, COUNT(*) AS query_count,
               AVG(confidence) AS avg_conf,
               MAX(created_at)::TEXT AS last_active
        FROM query_logs
        GROUP BY user_id
        ORDER BY query_count DESC
        """, nativeQuery = true)
    List<Object[]> getUserEngagementStats();

    @Query(value = """
        SELECT user_id::TEXT,
               COALESCE(SUM(estimated_cost_usd), 0) AS total_cost,
               COUNT(*) AS query_count
        FROM query_logs
        WHERE created_at >= :since
        GROUP BY user_id
        ORDER BY total_cost DESC
        """, nativeQuery = true)
    List<Object[]> getCostByUser(@Param("since") LocalDateTime since, Pageable pageable);

    @Query(value = """
        SELECT product, version,
               COALESCE(SUM(estimated_cost_usd), 0) AS total_cost,
               COUNT(*) AS query_count
        FROM query_logs
        WHERE created_at >= :since AND product IS NOT NULL
        GROUP BY product, version
        ORDER BY total_cost DESC
        """, nativeQuery = true)
    List<Object[]> getCostByProduct(@Param("since") LocalDateTime since);

    @Query(value = "SELECT COALESCE(SUM(estimated_cost_usd), 0) FROM query_logs WHERE created_at >= :since", nativeQuery = true)
    double sumCostSince(@Param("since") LocalDateTime since);

    @Query(value = "SELECT COALESCE(SUM(estimated_cost_usd), 0) FROM query_logs", nativeQuery = true)
    double sumCostAllTime();

    @Query(value = "SELECT AVG(confidence) FROM query_logs WHERE confidence IS NOT NULL AND created_at >= :since", nativeQuery = true)
    Double avgConfidenceSince(@Param("since") LocalDateTime since);

    @Query(value = """
        SELECT question_preview, COUNT(*) AS cnt, product, version
        FROM query_logs
        WHERE confidence < 0.6 AND created_at >= :since AND question_preview IS NOT NULL
        GROUP BY question_preview, product, version
        ORDER BY cnt DESC
        """, nativeQuery = true)
    List<Object[]> findFailedQueries(@Param("since") LocalDateTime since, Pageable pageable);

    @Query(value = """
        SELECT UNNEST(cited_documents) AS doc_name,
               COUNT(*) AS citations
        FROM query_logs
        WHERE cited_documents IS NOT NULL AND created_at >= :since
        GROUP BY doc_name
        ORDER BY citations DESC
        LIMIT 30
        """, nativeQuery = true)
    List<Object[]> getDocumentCitations(@Param("since") LocalDateTime since);
}

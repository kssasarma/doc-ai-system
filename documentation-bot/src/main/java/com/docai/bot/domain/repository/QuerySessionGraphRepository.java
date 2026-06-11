package com.docai.bot.domain.repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import com.docai.bot.domain.entity.QuerySessionGraph;

@Repository
public interface QuerySessionGraphRepository extends JpaRepository<QuerySessionGraph, UUID> {

    List<QuerySessionGraph> findBySessionId(UUID sessionId);

    @Query("""
        SELECT q FROM QuerySessionGraph q
        WHERE q.product = :product
          AND (:version IS NULL OR q.version = :version)
          AND q.askedAt >= :since
          AND q.sessionId <> :excludeSessionId
        ORDER BY q.askedAt DESC
        """)
    List<QuerySessionGraph> findRecentQueriesForProduct(
        String product, String version, LocalDateTime since, UUID excludeSessionId);

    @Query("""
        SELECT q FROM QuerySessionGraph q
        WHERE q.askedAt >= :since
        ORDER BY q.askedAt DESC
        """)
    List<QuerySessionGraph> findAllSince(LocalDateTime since);

    long countByAskedAtAfter(LocalDateTime since);
}

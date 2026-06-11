package com.docai.bot.domain.entity;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

import org.hibernate.annotations.CreationTimestamp;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "faq_clusters")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FaqCluster {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "product", length = 100)
    private String product;

    @Column(name = "version", length = 50)
    private String version;

    @Column(name = "canonical_question", columnDefinition = "TEXT", nullable = false)
    private String canonicalQuestion;

    @Column(name = "query_count", nullable = false)
    @Builder.Default
    private int queryCount = 0;

    @Column(name = "unique_users", nullable = false)
    @Builder.Default
    private int uniqueUsers = 0;

    @Column(name = "period_start", nullable = false)
    private LocalDate periodStart;

    @Column(name = "period_end", nullable = false)
    private LocalDate periodEnd;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
}

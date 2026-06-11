package com.docai.bot.domain.entity;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "documentation_gap_reports", indexes = {
    @Index(name = "idx_gap_reports_product", columnList = "product,version,report_period_end")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DocumentationGapReport {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "product", length = 100)
    private String product;

    @Column(name = "version", length = 50)
    private String version;

    @Column(name = "report_period_start", nullable = false)
    private LocalDate reportPeriodStart;

    @Column(name = "report_period_end", nullable = false)
    private LocalDate reportPeriodEnd;

    @Column(name = "total_low_confidence_queries", nullable = false)
    @Builder.Default
    private int totalLowConfidenceQueries = 0;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "gap_topics", columnDefinition = "JSONB", nullable = false)
    @Builder.Default
    private String gapTopics = "[]";

    @Column(name = "generated_at", nullable = false, updatable = false)
    private LocalDateTime generatedAt;

    @Column(name = "exported_at")
    private LocalDateTime exportedAt;
}

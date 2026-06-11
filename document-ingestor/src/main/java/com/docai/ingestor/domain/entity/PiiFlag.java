package com.docai.ingestor.domain.entity;

import java.time.LocalDateTime;
import java.util.UUID;

import org.hibernate.annotations.CreationTimestamp;

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
@Table(name = "pii_flags", indexes = {
    @Index(name = "idx_pii_flags_document",  columnList = "document_id"),
    @Index(name = "idx_pii_flags_tenant",    columnList = "tenant_id"),
    @Index(name = "idx_pii_flags_reviewed",  columnList = "reviewed")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PiiFlag {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "document_id", nullable = false)
    private UUID documentId;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "pii_type", nullable = false, length = 50)
    private String piiType;

    @Column(name = "occurrence_count", nullable = false)
    @Builder.Default
    private int occurrenceCount = 0;

    @Column(name = "sample_excerpt", length = 200)
    private String sampleExcerpt;

    @Column(name = "risk_level", nullable = false, length = 20)
    @Builder.Default
    private String riskLevel = "MEDIUM";

    @Column(name = "reviewed", nullable = false)
    @Builder.Default
    private boolean reviewed = false;

    @Column(name = "reviewed_by")
    private UUID reviewedBy;

    @Column(name = "reviewed_at")
    private LocalDateTime reviewedAt;

    @Column(name = "action_taken", length = 50)
    private String actionTaken;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
}

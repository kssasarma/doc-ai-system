package com.docai.bot.domain.entity;

import java.time.LocalDateTime;
import java.util.UUID;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

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
@Table(name = "data_retention_policies")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DataRetentionPolicy {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "tenant_id", nullable = false, unique = true)
    private UUID tenantId;

    @Column(name = "query_log_days", nullable = false)
    @Builder.Default
    private int queryLogDays = 365;

    @Column(name = "chat_session_days", nullable = false)
    @Builder.Default
    private int chatSessionDays = 730;

    @Column(name = "audit_log_days", nullable = false)
    @Builder.Default
    private int auditLogDays = 2555;

    @Column(name = "feedback_days", nullable = false)
    @Builder.Default
    private int feedbackDays = 365;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
}

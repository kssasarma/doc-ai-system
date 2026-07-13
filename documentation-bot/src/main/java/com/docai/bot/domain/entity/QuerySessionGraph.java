package com.docai.bot.domain.entity;

import java.time.LocalDateTime;
import java.util.UUID;

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
@Table(name = "query_session_graph", indexes = {
    @Index(name = "idx_qsg_session", columnList = "session_id,asked_at"),
    @Index(name = "idx_qsg_product", columnList = "product,version,asked_at")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class QuerySessionGraph {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "session_id", nullable = false)
    private UUID sessionId;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "user_id")
    private UUID userId;

    @Column(name = "query_text", columnDefinition = "TEXT", nullable = false)
    private String queryText;

    @Column(name = "product", length = 100)
    private String product;

    @Column(name = "version", length = 50)
    private String version;

    @Column(name = "asked_at", nullable = false)
    @Builder.Default
    private LocalDateTime askedAt = LocalDateTime.now();
}

package com.docai.ingestor.domain.entity;

import java.time.Instant;
import java.util.UUID;

import org.hibernate.annotations.CreationTimestamp;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "connector_sync_pages")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ConnectorSyncPage {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "token_id", nullable = false)
    private UUID tokenId;

    @Column(name = "document_id")
    private UUID documentId;

    @Column(nullable = false, length = 30)
    private String provider;

    @Column(name = "external_id", nullable = false, length = 200)
    private String externalId;

    @Column(length = 500)
    private String title;

    @Column(name = "space_key", length = 100)
    private String spaceKey;

    @Column(name = "last_synced_at")
    private Instant lastSyncedAt;

    @Column(name = "last_modified")
    private Instant lastModified;

    @Enumerated(EnumType.STRING)
    @Column(name = "sync_status", nullable = false, length = 20)
    @Builder.Default
    private SyncStatus syncStatus = SyncStatus.PENDING;

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public enum SyncStatus {
        PENDING, SYNCING, COMPLETED, FAILED, SKIPPED
    }
}

package com.docai.bot.domain.entity;

import java.time.LocalDateTime;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Read-only entity for querying personal notebooks from the ingestor-owned {@code notebooks}
 * table (document-ingestor's V11 migration creates and CRUDs this table via NotebookController;
 * this side only ever needs it to validate ownership when a chat request pins a notebookId — see
 * DocumentAccessPolicy#resolveNotebookScope).
 */
@Entity
@Table(name = "notebooks")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Notebook {

    @Id
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "owner_id", nullable = false)
    private UUID ownerId;

    @Column(nullable = false)
    private String name;

    private String description;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}

package com.docai.ingestor.domain.entity;

import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Data;

/**
 * Read-only view onto the {@code tenants} table — owned and migrated by documentation-bot, not
 * this service (both services share the same Postgres database; see application.yml). Only the
 * columns this service actually needs are mapped; never write through this entity.
 */
@Entity
@Table(name = "tenants")
@Data
public class Tenant {

    @Id
    private UUID id;

    @Column(name = "max_documents", nullable = false)
    private int maxDocuments;
}

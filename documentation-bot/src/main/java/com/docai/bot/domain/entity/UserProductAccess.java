package com.docai.bot.domain.entity;

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

/**
 * @deprecated Superseded by {@link DocumentAccess} (per-document, tenant-scoped grants) —
 * this product/version-string model was never actually consulted at retrieval time (nothing
 * checked it before answering a chat query). Retained only so the pre-Phase-4 admin UI
 * ("Users & Access" tab) doesn't 404; not read by any retrieval or eligibility code. Delete this
 * entity, its repository, {@code ProductAccessService}, and {@code ProductAccessController}
 * once the admin frontend is rebuilt against {@code DocumentAccessController}.
 */
@Deprecated
@Entity
@Table(name = "user_product_access")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserProductAccess {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "product", nullable = false, length = 100)
    private String product;

    @Column(name = "version", length = 50)
    private String version;

    @Column(name = "granted_by")
    private UUID grantedBy;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
}

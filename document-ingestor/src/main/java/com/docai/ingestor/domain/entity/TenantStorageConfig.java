package com.docai.ingestor.domain.entity;

import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Data;

/**
 * Read-only projection of the {@code tenant_storage_configs} table — owned and migrated by
 * documentation-bot (V39 migration). Never write through this entity.
 *
 * A row means the tenant has configured custom S3 storage; absence means use the platform default.
 */
@Entity
@Table(name = "tenant_storage_configs")
@Data
public class TenantStorageConfig {

    @Id
    private UUID id;

    @Column(name = "tenant_id", nullable = false, unique = true)
    private UUID tenantId;

    @Column(name = "s3_bucket", nullable = false, length = 200)
    private String s3Bucket;

    @Column(name = "s3_region", nullable = false, length = 100)
    private String s3Region;

    /** AES-256-GCM encrypted with SECRETS_ENCRYPTION_KEY. */
    @Column(name = "s3_access_key_enc", columnDefinition = "TEXT")
    private String s3AccessKeyEnc;

    /** AES-256-GCM encrypted with SECRETS_ENCRYPTION_KEY. */
    @Column(name = "s3_secret_key_enc", columnDefinition = "TEXT")
    private String s3SecretKeyEnc;

    @Column(name = "s3_endpoint", length = 500)
    private String s3Endpoint;

    @Column(name = "s3_path_style_access", nullable = false)
    private boolean s3PathStyleAccess;
}

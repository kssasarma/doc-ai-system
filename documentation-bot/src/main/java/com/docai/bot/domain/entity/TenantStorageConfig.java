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

/**
 * Per-tenant S3 storage configuration. A tenant with a row here uses their own S3 bucket and
 * credentials; tenants without a row fall back to the platform's default storage.
 *
 * {@code s3AccessKeyEnc} and {@code s3SecretKeyEnc} are AES-256-GCM encrypted by
 * {@link com.docai.bot.application.service.SecretsCryptoService} — the same scheme used for
 * per-tenant LLM API keys.
 */
@Entity
@Table(name = "tenant_storage_configs")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TenantStorageConfig {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "tenant_id", nullable = false, unique = true)
    private UUID tenantId;

    @Column(name = "s3_bucket", nullable = false, length = 200)
    private String s3Bucket;

    @Column(name = "s3_region", nullable = false, length = 100)
    @Builder.Default
    private String s3Region = "us-east-1";

    /** AES-256-GCM encrypted S3 access key ID. */
    @Column(name = "s3_access_key_enc", columnDefinition = "TEXT", nullable = false)
    private String s3AccessKeyEnc;

    /** AES-256-GCM encrypted S3 secret access key. */
    @Column(name = "s3_secret_key_enc", columnDefinition = "TEXT", nullable = false)
    private String s3SecretKeyEnc;

    /** Custom S3-compatible endpoint URL (e.g. MinIO, Backblaze B2). Null for real AWS S3. */
    @Column(name = "s3_endpoint", length = 500)
    private String s3Endpoint;

    /** True for path-style access (required by MinIO, SeaweedFS, Backblaze). False for AWS S3 virtual-hosted style. */
    @Column(name = "s3_path_style_access", nullable = false)
    @Builder.Default
    private boolean s3PathStyleAccess = false;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
}

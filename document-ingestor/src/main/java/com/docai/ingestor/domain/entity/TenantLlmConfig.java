package com.docai.ingestor.domain.entity;

import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Read-only view over documentation-bot's {@code tenant_llm_configs} table — both services share
 * one Postgres instance (see AGENT/README architecture notes), so the ingestor reads this
 * bot-owned table directly rather than duplicating tenant LLM config, the same pattern used
 * elsewhere for cross-service reads. Never written from this service.
 */
@Entity
@Table(name = "tenant_llm_configs")
@Data
@NoArgsConstructor
public class TenantLlmConfig {

    @Id
    private UUID id;

    @Column(name = "tenant_id", nullable = false, unique = true)
    private UUID tenantId;

    @Column(name = "embedding_provider", nullable = false, length = 50)
    private String embeddingProvider;

    @Column(name = "embedding_model", nullable = false, length = 100)
    private String embeddingModel;

    /** AES-256-GCM ciphertext (see documentation-bot's SecretsCryptoService) — decrypt with the
     * ingestor's own copy of the same service, sharing SECRETS_ENCRYPTION_KEY. */
    @Column(name = "api_key_enc", columnDefinition = "TEXT")
    private String apiKeyEnc;
}

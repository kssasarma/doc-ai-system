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
@Table(name = "tenant_llm_configs")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TenantLLMConfig {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "tenant_id", nullable = false, unique = true)
    private UUID tenantId;

    @Column(name = "chat_provider", nullable = false, length = 50)
    @Builder.Default
    private String chatProvider = "openai";

    @Column(name = "chat_model", nullable = false, length = 100)
    @Builder.Default
    private String chatModel = "gpt-4o-mini";

    @Column(name = "embedding_provider", nullable = false, length = 50)
    @Builder.Default
    private String embeddingProvider = "openai";

    @Column(name = "embedding_model", nullable = false, length = 100)
    @Builder.Default
    private String embeddingModel = "text-embedding-3-small";

    /** Overrides the ingestor's platform-default embedding batch token ceiling — null means "use
     * the platform default". Needed because a tenant's chosen embedding model may have a smaller
     * (or larger) context window than the platform default model. */
    @Column(name = "max_embedding_batch_tokens")
    private Integer maxEmbeddingBatchTokens;

    @Column(name = "api_key_enc", columnDefinition = "TEXT")
    private String apiKeyEnc;

    /** Separate key for the embedding provider (AES-256-GCM, same scheme as {@code apiKeyEnc}).
     * Null falls back to {@code apiKeyEnc} only when embeddingProvider equals chatProvider —
     * never to any platform-level key (none exists). */
    @Column(name = "embedding_api_key_enc", columnDefinition = "TEXT")
    private String embeddingApiKeyEnc;

    /** Chat provider endpoint override — null means the provider's canonical public endpoint.
     * Set for Azure OpenAI, proxies, or OpenAI-compatible self-hosted gateways. */
    @Column(name = "chat_base_url", length = 500)
    private String chatBaseUrl;

    /** Embedding provider endpoint override — same semantics as {@code chatBaseUrl}. */
    @Column(name = "embedding_base_url", length = 500)
    private String embeddingBaseUrl;

    /** Chat sampling temperature — previously a platform-wide value in application.yml. */
    @Column(name = "temperature", nullable = false)
    @Builder.Default
    private double temperature = 0.7;

    /** Max completion tokens per chat call — previously a platform-wide value in application.yml. */
    @Column(name = "max_tokens", nullable = false)
    @Builder.Default
    private int maxTokens = 5000;

    @Column(name = "azure_endpoint", length = 500)
    private String azureEndpoint;

    @Column(name = "azure_deployment", length = 100)
    private String azureDeployment;

    @Column(name = "routing_enabled", nullable = false)
    @Builder.Default
    private boolean routingEnabled = false;

    @Column(name = "simple_model", length = 100)
    @Builder.Default
    private String simpleModel = "gpt-4o-mini";

    @Column(name = "complex_model", length = 100)
    @Builder.Default
    private String complexModel = "gpt-4o";

    /** Model for {@link com.docai.bot.application.service.ReRankingService}'s LLM relevance
     * re-rank pass — independent of the simple/complex routing split above, since re-ranking is a
     * cheap, high-volume judgment call (not a full answer) that often warrants its own model
     * choice. Null means "inherit": {@code simpleModel} when {@code routingEnabled}, else
     * {@code chatModel}. */
    @Column(name = "rerank_model", length = 100)
    private String rerankModel;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
}

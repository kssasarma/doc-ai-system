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

    @Column(name = "api_key_enc", columnDefinition = "TEXT")
    private String apiKeyEnc;

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

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
}

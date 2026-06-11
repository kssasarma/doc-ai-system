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
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "tenants", indexes = {
    @Index(name = "idx_tenant_slug", columnList = "slug", unique = true)
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Tenant {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, length = 200)
    private String name;

    @Column(nullable = false, unique = true, length = 100)
    private String slug;

    @Column(nullable = false, length = 50)
    @Builder.Default
    private String plan = "FREE";

    @Column(nullable = false)
    @Builder.Default
    private boolean active = true;

    @Column(name = "max_users", nullable = false)
    @Builder.Default
    private int maxUsers = 10;

    @Column(name = "max_documents", nullable = false)
    @Builder.Default
    private int maxDocuments = 100;

    @Column(name = "oidc_enabled", nullable = false)
    @Builder.Default
    private boolean oidcEnabled = false;

    @Column(name = "oidc_provider", length = 100)
    private String oidcProvider;

    @Column(name = "oidc_issuer", length = 500)
    private String oidcIssuer;

    @Column(name = "oidc_client_id", length = 200)
    private String oidcClientId;

    @Column(name = "saml_enabled", nullable = false)
    @Builder.Default
    private boolean samlEnabled = false;

    @Column(name = "saml_idp_url", length = 500)
    private String samlIdpUrl;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
}

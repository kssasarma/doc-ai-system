package com.docai.bot.domain.entity;

import java.time.LocalDateTime;
import java.util.UUID;

import org.hibernate.annotations.CreationTimestamp;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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
@Table(name = "users", indexes = {
    @Index(name = "idx_user_username", columnList = "username", unique = true),
    @Index(name = "idx_user_email", columnList = "email", unique = true)
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, unique = true, length = 50)
    private String username;

    @Column(nullable = false, unique = true, length = 100)
    private String email;

    @Column(name = "password_hash", nullable = false)
    private String passwordHash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Role role;

    /** Null only for SUPER_ADMIN — every tenant-scoped role must have one. */
    @Column(name = "tenant_id")
    private UUID tenantId;

    /** True for seeded/reset accounts that must change their password before doing anything else. */
    @Builder.Default
    @Column(name = "must_change_password", nullable = false)
    private boolean mustChangePassword = false;

    // SSO / OIDC fields — null for password-auth users
    @Column(name = "oidc_sub", length = 500)
    private String oidcSub;

    @Column(name = "oidc_provider", length = 100)
    private String oidcProvider;

    @Column(name = "display_name", length = 200)
    private String displayName;

    @Column(name = "avatar_url", length = 500)
    private String avatarUrl;

    /** Set once by GdprErasureService — the account is anonymized and can no longer log in, but
     * the row itself is kept so shared/collaborative content elsewhere still resolves. */
    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    /** Admin-toggled, reversible login block — distinct from {@link #deletedAt} (permanent GDPR
     * erasure). Unlike erasure, deactivation is a known, disclosed state: the login error tells
     * the user exactly what happened rather than looking like a wrong password. */
    @Column(name = "deactivated_at")
    private LocalDateTime deactivatedAt;

    /** Consecutive bad-password count since the last successful login; reset to 0 on success. */
    @Builder.Default
    @Column(name = "failed_login_attempts", nullable = false)
    private int failedLoginAttempts = 0;

    /** Null unless currently locked out. Login is refused (even with the correct password) while
     * this is in the future — see UserService.authenticate. */
    @Column(name = "locked_until")
    private LocalDateTime lockedUntil;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    public enum Role {
        /** System-level. Creates tenants and each tenant's first ADMIN. Not scoped to any tenant. */
        SUPER_ADMIN,
        /** Tenant-scoped admin. Configures LLMs, uploads documents, grants document access, invites USERs. */
        ADMIN,
        USER
    }
}

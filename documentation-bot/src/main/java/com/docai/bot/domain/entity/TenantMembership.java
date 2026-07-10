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

/** One identity's membership in one tenant, with the role held there. A {@link User} can hold
 * several of these (Slack-workspace-style); exactly one tenant/role pair is "active" at a time,
 * cached denormalized on {@code User.tenantId}/{@code User.role} and carried in the JWT — see
 * {@code TenantMembershipService#switchActiveTenant}. SUPER_ADMIN accounts hold no membership
 * rows: they are not scoped to any single tenant. */
@Entity
@Table(name = "tenant_memberships", indexes = {
    @Index(name = "idx_tenant_memberships_user", columnList = "user_id"),
    @Index(name = "idx_tenant_memberships_tenant", columnList = "tenant_id")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TenantMembership {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private User.Role role;

    @CreationTimestamp
    @Column(name = "joined_at", nullable = false, updatable = false)
    private LocalDateTime joinedAt;
}

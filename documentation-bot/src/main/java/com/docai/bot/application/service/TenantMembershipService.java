package com.docai.bot.application.service;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.docai.bot.domain.entity.Tenant;
import com.docai.bot.domain.entity.TenantMembership;
import com.docai.bot.domain.entity.User;
import com.docai.bot.domain.repository.TenantMembershipRepository;
import com.docai.bot.domain.repository.TenantRepository;
import com.docai.bot.domain.repository.UserRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Owns the "which tenants can this identity act as, and which one is active right now" side of
 * the multi-tenant membership model. A {@link User} row still carries exactly one active
 * {@code tenantId}/{@code role} (cached there for every existing call site that reads it directly
 * — JWT issuance, audit exports, {@code ApiKeyAuthFilter} — none of which need to change); this
 * service is the source of truth for the full set of tenants an identity belongs to, and the only
 * place that's allowed to flip which one is active.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TenantMembershipService {

    private final TenantMembershipRepository membershipRepository;
    private final TenantRepository tenantRepository;
    private final UserRepository userRepository;

    @Transactional(readOnly = true)
    public List<MembershipDTO> listMyTenants(UUID userId) {
        List<TenantMembership> memberships = membershipRepository.findByUserId(userId);
        Map<UUID, Tenant> tenants = tenantRepository.findAllById(
                memberships.stream().map(TenantMembership::getTenantId).toList())
            .stream()
            .collect(Collectors.toMap(Tenant::getId, t -> t));

        return memberships.stream()
            .map(m -> {
                Tenant t = tenants.get(m.getTenantId());
                return new MembershipDTO(
                    m.getTenantId().toString(),
                    t != null ? t.getName() : "unknown",
                    m.getRole().name(),
                    m.getJoinedAt().toString());
            })
            .toList();
    }

    /**
     * Flips the caller's active tenant to one they already hold a membership in. Callers must
     * reissue a JWT from the returned {@link User} — its {@code tenantId}/{@code role} are now
     * the target tenant's, and the old token still carries the previous (still valid, but now
     * stale) active tenant until it expires.
     */
    @Transactional
    public User switchActiveTenant(UUID userId, UUID targetTenantId) {
        TenantMembership membership = membershipRepository.findByUserIdAndTenantId(userId, targetTenantId)
            .orElseThrow(() -> new IllegalArgumentException("Not a member of this tenant"));

        User user = userRepository.findById(userId)
            .orElseThrow(() -> new IllegalArgumentException("User not found: " + userId));

        user.setTenantId(membership.getTenantId());
        user.setRole(membership.getRole());
        User saved = userRepository.save(user);
        log.info("User {} switched active tenant to {}", userId, targetTenantId);
        return saved;
    }

    /** Idempotent: a membership that already exists is left as-is rather than duplicated. */
    @Transactional
    public void ensureMembership(UUID userId, UUID tenantId, User.Role role) {
        if (membershipRepository.existsByUserIdAndTenantId(userId, tenantId)) {
            return;
        }
        membershipRepository.save(TenantMembership.builder()
            .userId(userId)
            .tenantId(tenantId)
            .role(role)
            .build());
        log.info("Created membership: user {} in tenant {} as {}", userId, tenantId, role);
    }

    public record MembershipDTO(String tenantId, String tenantName, String role, String joinedAt) {}
}

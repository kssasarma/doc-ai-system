package com.docai.bot.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import com.docai.bot.PostgresTestContainerBase;
import com.docai.bot.application.service.JwtService;
import com.docai.bot.application.service.OidcJitProvisioningService;
import com.docai.bot.application.service.TenantMembershipService;
import com.docai.bot.config.TenantContext;
import com.docai.bot.domain.entity.Tenant;
import com.docai.bot.domain.entity.User;
import com.docai.bot.domain.repository.TenantMembershipRepository;
import com.docai.bot.domain.repository.TenantRepository;
import com.docai.bot.domain.repository.UserRepository;

/**
 * Covers the multi-tenant fix: the same OIDC identity (sub+provider) logging into a *second*
 * tenant must gain a membership there (not silently keep acting as tenant A), and each login's
 * tenant becomes the active one — see {@link OidcJitProvisioningService} class Javadoc.
 */
@Transactional
class OidcJitProvisioningServiceTest extends PostgresTestContainerBase {

    @Autowired OidcJitProvisioningService oidcJitProvisioningService;
    @Autowired TenantMembershipService membershipService;
    @Autowired TenantMembershipRepository membershipRepository;
    @Autowired TenantRepository tenantRepository;
    @Autowired UserRepository userRepository;
    @Autowired JwtService jwtService;

    @Test
    void firstLogin_provisionsNewUserAndFirstMembership() {
        Tenant tenant = persistTenant("tenant-oidc-first");
        TenantContext.set(tenant.getId());
        try {
            String token = oidcJitProvisioningService.provisionAndIssueToken("google", claims("sub-1", "person1@example.com"));

            UUID userId = jwtService.extractUserId(token);
            User user = userRepository.findById(userId).orElseThrow();
            assertThat(user.getTenantId()).isEqualTo(tenant.getId());
            assertThat(user.getRole()).isEqualTo(User.Role.USER);
            assertThat(membershipRepository.existsByUserIdAndTenantId(userId, tenant.getId())).isTrue();
        } finally {
            TenantContext.clear();
        }
    }

    @Test
    void sameIdentity_loggingIntoSecondTenant_gainsMembershipAndSwitchesActive() {
        Tenant tenantA = persistTenant("tenant-oidc-a");
        Tenant tenantB = persistTenant("tenant-oidc-b");

        TenantContext.set(tenantA.getId());
        String firstToken;
        try {
            firstToken = oidcJitProvisioningService.provisionAndIssueToken("google", claims("sub-2", "person2@example.com"));
        } finally {
            TenantContext.clear();
        }
        UUID userId = jwtService.extractUserId(firstToken);

        TenantContext.set(tenantB.getId());
        String secondToken;
        try {
            secondToken = oidcJitProvisioningService.provisionAndIssueToken("google", claims("sub-2", "person2@example.com"));
        } finally {
            TenantContext.clear();
        }

        assertThat(jwtService.extractUserId(secondToken)).isEqualTo(userId);
        assertThat(jwtService.extractTenantId(secondToken)).isEqualTo(tenantB.getId());

        User user = userRepository.findById(userId).orElseThrow();
        assertThat(user.getTenantId()).isEqualTo(tenantB.getId());
        assertThat(membershipRepository.existsByUserIdAndTenantId(userId, tenantA.getId())).isTrue();
        assertThat(membershipRepository.existsByUserIdAndTenantId(userId, tenantB.getId())).isTrue();
        assertThat(membershipService.listMyTenants(userId)).hasSize(2);
    }

    @Test
    void reLogin_preservesAPromotedRole_ratherThanResettingToUser() {
        Tenant tenant = persistTenant("tenant-oidc-promoted");
        TenantContext.set(tenant.getId());
        String token;
        try {
            token = oidcJitProvisioningService.provisionAndIssueToken("google", claims("sub-3", "person3@example.com"));
        } finally {
            TenantContext.clear();
        }
        UUID userId = jwtService.extractUserId(token);

        // Simulate a tenant admin promoting this JIT-provisioned user to ADMIN after the fact.
        var membership = membershipRepository.findByUserIdAndTenantId(userId, tenant.getId()).orElseThrow();
        membership.setRole(User.Role.ADMIN);
        membershipRepository.save(membership);

        TenantContext.set(tenant.getId());
        String reloginToken;
        try {
            reloginToken = oidcJitProvisioningService.provisionAndIssueToken("google", claims("sub-3", "person3@example.com"));
        } finally {
            TenantContext.clear();
        }

        assertThat(jwtService.extractRole(reloginToken)).isEqualTo("ADMIN");
        assertThat(userRepository.findById(userId).orElseThrow().getRole()).isEqualTo(User.Role.ADMIN);
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private Tenant persistTenant(String slug) {
        return tenantRepository.save(Tenant.builder().name(slug).slug(slug).build());
    }

    private Map<String, Object> claims(String sub, String email) {
        return Map.of("sub", sub, "email", email, "name", "Test Person");
    }
}

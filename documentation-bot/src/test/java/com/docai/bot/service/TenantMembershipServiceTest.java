package com.docai.bot.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import com.docai.bot.PostgresTestContainerBase;
import com.docai.bot.application.service.TenantMembershipService;
import com.docai.bot.application.service.TenantMembershipService.MembershipDTO;
import com.docai.bot.domain.entity.Tenant;
import com.docai.bot.domain.entity.User;
import com.docai.bot.domain.repository.TenantMembershipRepository;
import com.docai.bot.domain.repository.TenantRepository;
import com.docai.bot.domain.repository.UserRepository;

/**
 * Covers the multi-tenant membership model: an identity can hold memberships in more than one
 * tenant, exactly one of which is "active" (cached on {@code User.tenantId}/{@code role} and
 * carried in the JWT). See {@code InvitationServiceTest} for how memberships get created via the
 * invite/accept flow.
 */
@Transactional
class TenantMembershipServiceTest extends PostgresTestContainerBase {

    @Autowired TenantMembershipService membershipService;
    @Autowired TenantMembershipRepository membershipRepository;
    @Autowired TenantRepository tenantRepository;
    @Autowired UserRepository userRepository;

    @Test
    void ensureMembership_thenListMyTenants_returnsIt() {
        Tenant tenant = persistTenant("tenant-list");
        User user = persistUser(tenant, User.Role.USER);

        membershipService.ensureMembership(user.getId(), tenant.getId(), User.Role.USER);

        var memberships = membershipService.listMyTenants(user.getId());
        assertThat(memberships).hasSize(1);
        assertThat(memberships.get(0).tenantId()).isEqualTo(tenant.getId().toString());
        assertThat(memberships.get(0).role()).isEqualTo("USER");
    }

    @Test
    void ensureMembership_isIdempotent_secondCallDoesNotDuplicate() {
        Tenant tenant = persistTenant("tenant-idempotent");
        User user = persistUser(tenant, User.Role.USER);

        membershipService.ensureMembership(user.getId(), tenant.getId(), User.Role.USER);
        membershipService.ensureMembership(user.getId(), tenant.getId(), User.Role.USER);

        assertThat(membershipService.listMyTenants(user.getId())).hasSize(1);
    }

    @Test
    void userWithTwoMemberships_listsBoth() {
        Tenant tenantA = persistTenant("tenant-a-multi");
        Tenant tenantB = persistTenant("tenant-b-multi");
        User user = persistUser(tenantA, User.Role.USER);

        membershipService.ensureMembership(user.getId(), tenantA.getId(), User.Role.USER);
        membershipService.ensureMembership(user.getId(), tenantB.getId(), User.Role.ADMIN);

        var memberships = membershipService.listMyTenants(user.getId());
        assertThat(memberships).extracting(MembershipDTO::tenantId)
            .containsExactlyInAnyOrder(tenantA.getId().toString(), tenantB.getId().toString());
        assertThat(memberships.stream().filter(m -> m.tenantId().equals(tenantB.getId().toString())).findFirst().orElseThrow().role())
            .isEqualTo("ADMIN");
    }

    @Test
    void switchActiveTenant_updatesTheUsersActiveTenantAndRole() {
        Tenant tenantA = persistTenant("tenant-a-switch");
        Tenant tenantB = persistTenant("tenant-b-switch");
        User user = persistUser(tenantA, User.Role.USER);
        membershipService.ensureMembership(user.getId(), tenantA.getId(), User.Role.USER);
        membershipService.ensureMembership(user.getId(), tenantB.getId(), User.Role.ADMIN);

        User switched = membershipService.switchActiveTenant(user.getId(), tenantB.getId());

        assertThat(switched.getTenantId()).isEqualTo(tenantB.getId());
        assertThat(switched.getRole()).isEqualTo(User.Role.ADMIN);

        User reloaded = userRepository.findById(user.getId()).orElseThrow();
        assertThat(reloaded.getTenantId()).isEqualTo(tenantB.getId());
        assertThat(reloaded.getRole()).isEqualTo(User.Role.ADMIN);
    }

    @Test
    void switchActiveTenant_toATenantNotAMemberOf_throws() {
        Tenant tenantA = persistTenant("tenant-a-noswitch");
        Tenant tenantB = persistTenant("tenant-b-noswitch");
        User user = persistUser(tenantA, User.Role.USER);
        membershipService.ensureMembership(user.getId(), tenantA.getId(), User.Role.USER);

        assertThatThrownBy(() -> membershipService.switchActiveTenant(user.getId(), tenantB.getId()))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Not a member");
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private Tenant persistTenant(String slug) {
        return tenantRepository.save(Tenant.builder().name(slug).slug(slug).build());
    }

    private User persistUser(Tenant tenant, User.Role role) {
        String unique = UUID.randomUUID().toString().substring(0, 8);
        return userRepository.save(User.builder()
            .username(role.name().toLowerCase() + "-" + unique)
            .email(role.name().toLowerCase() + "-" + unique + "@example.com")
            .passwordHash("irrelevant-for-this-test")
            .role(role)
            .tenantId(tenant.getId())
            .build());
    }
}

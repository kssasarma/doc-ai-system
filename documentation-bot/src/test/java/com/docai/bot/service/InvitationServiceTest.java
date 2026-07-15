package com.docai.bot.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;

import com.docai.bot.PostgresTestContainerBase;
import com.docai.bot.application.service.InvitationService;
import com.docai.bot.application.service.TenantMembershipService;
import com.docai.bot.domain.entity.Tenant;
import com.docai.bot.domain.entity.User;
import com.docai.bot.domain.repository.TenantMembershipRepository;
import com.docai.bot.domain.repository.TenantRepository;
import com.docai.bot.domain.repository.UserRepository;

/**
 * Covers both invitation paths: provisioning a brand-new identity (unchanged from before the
 * multi-tenant membership migration) and joining an *existing* identity into a second tenant
 * (the new capability — see {@link InvitationService} class Javadoc).
 */
@Transactional
class InvitationServiceTest extends PostgresTestContainerBase {

    @Autowired InvitationService invitationService;
    @Autowired TenantMembershipService membershipService;
    @Autowired TenantMembershipRepository membershipRepository;
    @Autowired TenantRepository tenantRepository;
    @Autowired UserRepository userRepository;
    @Autowired PasswordEncoder passwordEncoder;

    @Test
    void invite_thenAccept_newIdentity_createsUserAndFirstMembership() {
        Tenant tenant = persistTenant("tenant-new-identity");
        User admin = persistUser(tenant, User.Role.ADMIN);

        InvitationService.InviteResult invitation = invitationService.invite("newperson@example.com", User.Role.USER, tenant.getId(), admin.getId());
        User created = invitationService.accept(invitation.rawToken(), "newperson", "SecurePass123!");

        assertThat(created.getEmail()).isEqualTo("newperson@example.com");
        assertThat(created.getTenantId()).isEqualTo(tenant.getId());
        assertThat(created.getRole()).isEqualTo(User.Role.USER);
        assertThat(membershipRepository.existsByUserIdAndTenantId(created.getId(), tenant.getId())).isTrue();
    }

    @Test
    void invite_existingIdentityNotYetInThisTenant_isAllowed() {
        Tenant tenantA = persistTenant("tenant-a-existing-invite");
        Tenant tenantB = persistTenant("tenant-b-existing-invite");
        User adminB = persistUser(tenantB, User.Role.ADMIN);
        User existingPerson = persistUser(tenantA, User.Role.USER);

        InvitationService.InviteResult invitation = invitationService.invite(existingPerson.getEmail(), User.Role.USER, tenantB.getId(), adminB.getId());

        assertThat(invitation.invitation().getEmail()).isEqualTo(existingPerson.getEmail());
        assertThat(invitation.invitation().getTenantId()).isEqualTo(tenantB.getId());
    }

    @Test
    void invite_existingIdentityAlreadyMemberOfThisTenant_throws() {
        Tenant tenant = persistTenant("tenant-already-member");
        User admin = persistUser(tenant, User.Role.ADMIN);
        User existingMember = persistUser(tenant, User.Role.USER);
        membershipService.ensureMembership(existingMember.getId(), tenant.getId(), User.Role.USER);

        assertThatThrownBy(() -> invitationService.invite(existingMember.getEmail(), User.Role.USER, tenant.getId(), admin.getId()))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("already a member");
    }

    @Test
    void accept_existingIdentity_withCorrectCredentials_joinsSecondTenantAndSwitchesActive() {
        Tenant tenantA = persistTenant("tenant-a-join");
        Tenant tenantB = persistTenant("tenant-b-join");
        User adminB = persistUser(tenantB, User.Role.ADMIN);
        User existingPerson = userRepository.save(User.builder()
            .username("existing-person")
            .email("existing-person@example.com")
            .passwordHash(passwordEncoder.encode("MyRealPassword1!"))
            .role(User.Role.USER)
            .tenantId(tenantA.getId())
            .build());
        membershipService.ensureMembership(existingPerson.getId(), tenantA.getId(), User.Role.USER);

        InvitationService.InviteResult invitation = invitationService.invite(existingPerson.getEmail(), User.Role.ADMIN, tenantB.getId(), adminB.getId());
        User result = invitationService.accept(invitation.rawToken(), "existing-person", "MyRealPassword1!");

        assertThat(result.getId()).isEqualTo(existingPerson.getId());
        assertThat(result.getTenantId()).isEqualTo(tenantB.getId());
        assertThat(result.getRole()).isEqualTo(User.Role.ADMIN);
        assertThat(membershipRepository.existsByUserIdAndTenantId(existingPerson.getId(), tenantA.getId())).isTrue();
        assertThat(membershipRepository.existsByUserIdAndTenantId(existingPerson.getId(), tenantB.getId())).isTrue();
        assertThat(membershipService.listMyTenants(existingPerson.getId())).hasSize(2);
    }

    @Test
    void accept_existingIdentity_withWrongPassword_throwsAndDoesNotCreateMembership() {
        Tenant tenantA = persistTenant("tenant-a-wrongpass");
        Tenant tenantB = persistTenant("tenant-b-wrongpass");
        User adminB = persistUser(tenantB, User.Role.ADMIN);
        User existingPerson = userRepository.save(User.builder()
            .username("existing-person-2")
            .email("existing-person-2@example.com")
            .passwordHash(passwordEncoder.encode("MyRealPassword1!"))
            .role(User.Role.USER)
            .tenantId(tenantA.getId())
            .build());

        InvitationService.InviteResult invitation = invitationService.invite(existingPerson.getEmail(), User.Role.USER, tenantB.getId(), adminB.getId());

        assertThatThrownBy(() -> invitationService.accept(invitation.rawToken(), "existing-person-2", "WrongPassword"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Incorrect username or password");
        assertThat(membershipRepository.existsByUserIdAndTenantId(existingPerson.getId(), tenantB.getId())).isFalse();
    }

    @Test
    void accept_invalidToken_throws() {
        assertThatThrownBy(() -> invitationService.accept("not-a-real-token", "someone", "password123"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Invalid invitation");
    }

    @Test
    void accept_alreadyAccepted_throws() {
        Tenant tenant = persistTenant("tenant-double-accept");
        User admin = persistUser(tenant, User.Role.ADMIN);
        InvitationService.InviteResult invitation = invitationService.invite("doubleaccept@example.com", User.Role.USER, tenant.getId(), admin.getId());
        invitationService.accept(invitation.rawToken(), "doubleaccept", "SecurePass123!");

        assertThatThrownBy(() -> invitationService.accept(invitation.rawToken(), "doubleaccept2", "SecurePass123!"))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("already been used");
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

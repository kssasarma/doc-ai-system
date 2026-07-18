package com.docai.bot.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import com.docai.bot.PostgresTestContainerBase;
import com.docai.bot.application.service.GroupService;
import com.docai.bot.application.service.GroupService.GroupDTO;
import com.docai.bot.domain.entity.Tenant;
import com.docai.bot.domain.entity.User;
import com.docai.bot.domain.repository.TenantRepository;
import com.docai.bot.domain.repository.UserRepository;

/**
 * CRUD and tenant-isolation coverage for {@link GroupService} — the group/membership management
 * layer that sits behind {@code GroupDocumentAccessService} and the group union in
 * {@code GrantBasedDocumentAccessPolicy} (exercised end-to-end by {@link DocumentAccessIsolationTest}).
 */
@Transactional
class GroupServiceTest extends PostgresTestContainerBase {

    @Autowired GroupService groupService;
    @Autowired TenantRepository tenantRepository;
    @Autowired UserRepository userRepository;

    @Test
    void create_thenList_returnsTheGroupWithZeroMembers() {
        Tenant tenant = persistTenant("tenant-create");
        User admin = persistUser(tenant, User.Role.ADMIN);

        GroupDTO created = groupService.create("Support Team", tenant.getId(), admin.getId());

        assertThat(created.name()).isEqualTo("Support Team");
        assertThat(created.memberCount()).isZero();
        assertThat(groupService.list(tenant.getId(), null)).extracting(GroupDTO::id).containsExactly(created.id());
    }

    @Test
    void create_duplicateNameInSameTenant_throws() {
        Tenant tenant = persistTenant("tenant-dup");
        User admin = persistUser(tenant, User.Role.ADMIN);
        groupService.create("Support Team", tenant.getId(), admin.getId());

        assertThatThrownBy(() -> groupService.create("Support Team", tenant.getId(), admin.getId()))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("already exists");
    }

    @Test
    void create_sameNameInDifferentTenants_isAllowed() {
        Tenant tenantA = persistTenant("tenant-a-samename");
        Tenant tenantB = persistTenant("tenant-b-samename");
        User adminA = persistUser(tenantA, User.Role.ADMIN);
        User adminB = persistUser(tenantB, User.Role.ADMIN);

        groupService.create("Support Team", tenantA.getId(), adminA.getId());

        GroupDTO createdInB = groupService.create("Support Team", tenantB.getId(), adminB.getId());
        assertThat(createdInB.name()).isEqualTo("Support Team");
    }

    @Test
    void create_blankName_throws() {
        Tenant tenant = persistTenant("tenant-blank");
        User admin = persistUser(tenant, User.Role.ADMIN);

        assertThatThrownBy(() -> groupService.create("   ", tenant.getId(), admin.getId()))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void addMember_thenListMembers_returnsTheUser() {
        Tenant tenant = persistTenant("tenant-add-member");
        User admin = persistUser(tenant, User.Role.ADMIN);
        User user = persistUser(tenant, User.Role.USER);
        GroupDTO group = groupService.create("Team", tenant.getId(), admin.getId());
        UUID groupId = UUID.fromString(group.id());

        groupService.addMember(groupId, user.getId(), tenant.getId());

        assertThat(groupService.listMembers(groupId, tenant.getId()))
            .extracting(GroupService.MemberDTO::userId)
            .containsExactly(user.getId().toString());
        assertThat(groupService.list(tenant.getId(), null).get(0).memberCount()).isEqualTo(1);
    }

    @Test
    void addMember_alreadyAMember_throws() {
        Tenant tenant = persistTenant("tenant-dup-member");
        User admin = persistUser(tenant, User.Role.ADMIN);
        User user = persistUser(tenant, User.Role.USER);
        GroupDTO group = groupService.create("Team", tenant.getId(), admin.getId());
        UUID groupId = UUID.fromString(group.id());
        groupService.addMember(groupId, user.getId(), tenant.getId());

        assertThatThrownBy(() -> groupService.addMember(groupId, user.getId(), tenant.getId()))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("already a member");
    }

    @Test
    void addMember_userFromAnotherTenant_throws() {
        Tenant tenantA = persistTenant("tenant-a-cross-member");
        Tenant tenantB = persistTenant("tenant-b-cross-member");
        User adminA = persistUser(tenantA, User.Role.ADMIN);
        User userB = persistUser(tenantB, User.Role.USER);
        GroupDTO group = groupService.create("Team", tenantA.getId(), adminA.getId());
        UUID groupId = UUID.fromString(group.id());

        assertThatThrownBy(() -> groupService.addMember(groupId, userB.getId(), tenantA.getId()))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("not found");
    }

    @Test
    void removeMember_thenListMembers_isEmpty() {
        Tenant tenant = persistTenant("tenant-remove-member");
        User admin = persistUser(tenant, User.Role.ADMIN);
        User user = persistUser(tenant, User.Role.USER);
        GroupDTO group = groupService.create("Team", tenant.getId(), admin.getId());
        UUID groupId = UUID.fromString(group.id());
        groupService.addMember(groupId, user.getId(), tenant.getId());

        groupService.removeMember(groupId, user.getId(), tenant.getId());

        assertThat(groupService.listMembers(groupId, tenant.getId())).isEmpty();
    }

    @Test
    void delete_removesTheGroupFromList() {
        Tenant tenant = persistTenant("tenant-delete");
        User admin = persistUser(tenant, User.Role.ADMIN);
        GroupDTO group = groupService.create("Team", tenant.getId(), admin.getId());

        groupService.delete(UUID.fromString(group.id()), tenant.getId());

        assertThat(groupService.list(tenant.getId(), null)).isEmpty();
    }

    @Test
    void operationsOnGroupFromAnotherTenant_throw() {
        Tenant tenantA = persistTenant("tenant-a-crossop");
        Tenant tenantB = persistTenant("tenant-b-crossop");
        User adminA = persistUser(tenantA, User.Role.ADMIN);
        GroupDTO group = groupService.create("Team", tenantA.getId(), adminA.getId());
        UUID groupId = UUID.fromString(group.id());

        assertThatThrownBy(() -> groupService.delete(groupId, tenantB.getId()))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("not found");
        assertThatThrownBy(() -> groupService.listMembers(groupId, tenantB.getId()))
            .isInstanceOf(IllegalArgumentException.class);
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

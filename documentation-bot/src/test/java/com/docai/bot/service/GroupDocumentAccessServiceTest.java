package com.docai.bot.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import com.docai.bot.PostgresTestContainerBase;
import com.docai.bot.application.service.GroupDocumentAccessService;
import com.docai.bot.application.service.GroupDocumentAccessService.GroupGranteeDTO;
import com.docai.bot.application.service.GroupService;
import com.docai.bot.application.service.GroupService.GroupDTO;
import com.docai.bot.domain.entity.Document;
import com.docai.bot.domain.entity.Tenant;
import com.docai.bot.domain.entity.User;
import com.docai.bot.domain.repository.DocumentRepository;
import com.docai.bot.domain.repository.TenantRepository;
import com.docai.bot.domain.repository.UserRepository;

/**
 * CRUD and tenant-isolation coverage for {@link GroupDocumentAccessService} — the group-grant
 * counterpart to {@code DocumentAccessService}. Effective retrieval-scope behavior (whether a
 * grant actually widens what a group member can search) is covered end-to-end by
 * {@link DocumentAccessIsolationTest}; this class covers the CRUD/validation surface only.
 */
@Transactional
class GroupDocumentAccessServiceTest extends PostgresTestContainerBase {

    @Autowired GroupDocumentAccessService groupDocumentAccessService;
    @Autowired GroupService groupService;
    @Autowired DocumentRepository documentRepository;
    @Autowired TenantRepository tenantRepository;
    @Autowired UserRepository userRepository;

    @Test
    void grant_thenListGrantedGroups_returnsTheGroup() {
        Tenant tenant = persistTenant("tenant-grant");
        User admin = persistUser(tenant, User.Role.ADMIN);
        Document doc = persistDocument(tenant, "Doc1");
        GroupDTO group = groupService.create("Team", tenant.getId(), admin.getId());
        UUID groupId = UUID.fromString(group.id());

        GroupGranteeDTO grant = groupDocumentAccessService.grant(doc.getId(), groupId, tenant.getId(), admin.getId());

        assertThat(grant.groupId()).isEqualTo(groupId.toString());
        assertThat(grant.groupName()).isEqualTo("Team");
        assertThat(groupDocumentAccessService.listGrantedGroups(doc.getId(), tenant.getId()))
            .extracting(GroupGranteeDTO::groupId)
            .containsExactly(groupId.toString());
    }

    @Test
    void grant_isIdempotent_reGrantingDoesNotDuplicate() {
        Tenant tenant = persistTenant("tenant-idempotent");
        User admin = persistUser(tenant, User.Role.ADMIN);
        Document doc = persistDocument(tenant, "Doc1");
        GroupDTO group = groupService.create("Team", tenant.getId(), admin.getId());
        UUID groupId = UUID.fromString(group.id());

        groupDocumentAccessService.grant(doc.getId(), groupId, tenant.getId(), admin.getId());
        groupDocumentAccessService.grant(doc.getId(), groupId, tenant.getId(), admin.getId());

        assertThat(groupDocumentAccessService.listGrantedGroups(doc.getId(), tenant.getId())).hasSize(1);
    }

    @Test
    void revoke_removesTheGrant() {
        Tenant tenant = persistTenant("tenant-revoke");
        User admin = persistUser(tenant, User.Role.ADMIN);
        Document doc = persistDocument(tenant, "Doc1");
        GroupDTO group = groupService.create("Team", tenant.getId(), admin.getId());
        UUID groupId = UUID.fromString(group.id());
        groupDocumentAccessService.grant(doc.getId(), groupId, tenant.getId(), admin.getId());

        groupDocumentAccessService.revoke(doc.getId(), groupId, tenant.getId());

        assertThat(groupDocumentAccessService.listGrantedGroups(doc.getId(), tenant.getId())).isEmpty();
    }

    @Test
    void grant_documentFromAnotherTenant_throws() {
        Tenant tenantA = persistTenant("tenant-a-crossdoc");
        Tenant tenantB = persistTenant("tenant-b-crossdoc");
        User adminA = persistUser(tenantA, User.Role.ADMIN);
        Document docInB = persistDocument(tenantB, "Doc1");
        GroupDTO group = groupService.create("Team", tenantA.getId(), adminA.getId());
        UUID groupId = UUID.fromString(group.id());

        assertThatThrownBy(() -> groupDocumentAccessService.grant(docInB.getId(), groupId, tenantA.getId(), adminA.getId()))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("not found");
    }

    @Test
    void grant_groupFromAnotherTenant_throws() {
        Tenant tenantA = persistTenant("tenant-a-crossgroup");
        Tenant tenantB = persistTenant("tenant-b-crossgroup");
        User adminA = persistUser(tenantA, User.Role.ADMIN);
        User adminB = persistUser(tenantB, User.Role.ADMIN);
        Document doc = persistDocument(tenantA, "Doc1");
        GroupDTO groupInB = groupService.create("Team", tenantB.getId(), adminB.getId());
        UUID groupIdInB = UUID.fromString(groupInB.id());

        assertThatThrownBy(() -> groupDocumentAccessService.grant(doc.getId(), groupIdInB, tenantA.getId(), adminA.getId()))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("not found");
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

    private Document persistDocument(Tenant tenant, String name) {
        Document doc = Document.builder()
            .id(UUID.randomUUID())
            .tenantId(tenant.getId())
            .product("product-x")
            .version("1.0")
            .documentName(name)
            .status("COMPLETED")
            .build();
        return documentRepository.save(doc);
    }
}

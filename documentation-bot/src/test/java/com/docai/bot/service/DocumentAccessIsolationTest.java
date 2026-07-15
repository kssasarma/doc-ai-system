package com.docai.bot.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.ai.embedding.Embedding;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.embedding.EmbeddingResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;

import com.docai.bot.PostgresTestContainerBase;
import com.docai.bot.application.service.DocumentAccessPolicy;
import com.docai.bot.application.service.DocumentAccessService;
import com.docai.bot.application.service.GroupDocumentAccessService;
import com.docai.bot.application.service.GroupService;
import com.docai.bot.application.service.VectorSearchService;
import com.docai.bot.domain.entity.Document;
import com.docai.bot.domain.entity.DocumentChunk;
import com.docai.bot.domain.entity.Tenant;
import com.docai.bot.domain.entity.User;
import com.docai.bot.domain.model.RetrievedChunk;
import com.docai.bot.domain.model.SearchScope;
import com.docai.bot.domain.repository.DocumentChunkRepository;
import com.docai.bot.domain.repository.DocumentRepository;
import com.docai.bot.domain.repository.TenantRepository;
import com.docai.bot.domain.repository.UserRepository;

/**
 * Proves the Phase 2 access-control guarantee end-to-end against a real Postgres + pgvector
 * database: a user sees only documents explicitly granted to them (or, for ADMIN, their whole
 * tenant's corpus) — never another user's ungranted documents, and never another tenant's
 * documents even in a defense-in-depth worst case where a document ID leaks across the tenant
 * boundary into a SearchScope by mistake.
 */
@Transactional
class DocumentAccessIsolationTest extends PostgresTestContainerBase {

    @Autowired DocumentAccessPolicy documentAccessPolicy;
    @Autowired DocumentAccessService documentAccessService;
    @Autowired GroupService groupService;
    @Autowired GroupDocumentAccessService groupDocumentAccessService;
    @Autowired VectorSearchService vectorSearchService;
    @Autowired DocumentRepository documentRepository;
    @Autowired DocumentChunkRepository chunkRepository;
    @Autowired TenantRepository tenantRepository;
    @Autowired UserRepository userRepository;
    @MockitoBean EmbeddingModel embeddingModel;

    private static final int DIMS = 1536;

    @Test
    void userWithNoGrants_getsEmptyScopeAndSkipsSearchEntirely() {
        Tenant tenantA = persistTenant("tenant-a-none");
        User user = persistUser(tenantA, User.Role.USER);
        Document doc = persistDocument(tenantA, "product-x", "1.0", "Doc1");
        persistChunk(doc.getId(), "apple pie recipe", unitVector());

        SearchScope scope = documentAccessPolicy.resolveScope(user.getId(), tenantA.getId());
        assertThat(scope.isEmpty()).isTrue();

        List<RetrievedChunk> results = vectorSearchService.search("recipe", scope);
        assertThat(results).isEmpty();
        verify(embeddingModel, never()).call(any(EmbeddingRequest.class));
    }

    @Test
    void userWithGrant_seesOnlyTheGrantedDocument() {
        Tenant tenantA = persistTenant("tenant-a-grant");
        User admin = persistUser(tenantA, User.Role.ADMIN);
        User user = persistUser(tenantA, User.Role.USER);

        Document granted = persistDocument(tenantA, "product-x", "1.0", "Granted Doc");
        Document ungranted = persistDocument(tenantA, "product-x", "1.0", "Ungranted Doc");
        persistChunk(granted.getId(), "apple pie recipe", unitVector());
        persistChunk(ungranted.getId(), "banana bread recipe", unitVector());

        documentAccessService.grant(granted.getId(), user.getId(), tenantA.getId(), admin.getId());
        stubEmbedding(unitVector());

        SearchScope scope = documentAccessPolicy.resolveScope(user.getId(), tenantA.getId());
        assertThat(scope.documentIds()).containsExactly(granted.getId());

        List<RetrievedChunk> results = vectorSearchService.search("recipe", scope);
        assertThat(results).hasSize(1);
        assertThat(results.get(0).getDocumentName()).isEqualTo("Granted Doc");
    }

    @Test
    void admin_seesWholeTenantCorpusWithoutAnyExplicitGrant() {
        Tenant tenantA = persistTenant("tenant-a-admin");
        User admin = persistUser(tenantA, User.Role.ADMIN);

        Document doc1 = persistDocument(tenantA, "product-x", "1.0", "Doc1");
        Document doc2 = persistDocument(tenantA, "product-x", "1.0", "Doc2");
        persistChunk(doc1.getId(), "apple pie recipe", unitVector());
        persistChunk(doc2.getId(), "banana bread recipe", unitVector());
        stubEmbedding(unitVector());

        SearchScope scope = documentAccessPolicy.resolveScope(admin.getId(), tenantA.getId());
        assertThat(scope.documentIds()).containsExactlyInAnyOrder(doc1.getId(), doc2.getId());

        List<RetrievedChunk> results = vectorSearchService.search("recipe", scope);
        assertThat(results).hasSize(2);
    }

    @Test
    void crossTenantDocumentId_inScopeIsStillExcluded_byTenantFilterInTheQueryItself() {
        Tenant tenantA = persistTenant("tenant-a-cross");
        Tenant tenantB = persistTenant("tenant-b-cross");

        Document docInTenantA = persistDocument(tenantA, "product-x", "1.0", "Tenant A Secret");
        persistChunk(docInTenantA.getId(), "confidential apple pie recipe", unitVector());
        stubEmbedding(unitVector());

        // Simulates a hypothetical bug elsewhere that leaked tenant A's document id into a
        // scope resolved for tenant B — the query's own tenant_id filter must still block it.
        SearchScope corruptedScope = new SearchScope(tenantB.getId(), Set.of(docInTenantA.getId()));

        List<RetrievedChunk> results = vectorSearchService.search("recipe", corruptedScope);
        assertThat(results).isEmpty();
    }

    @Test
    void userWithGroupGrant_seesOnlyTheGroupGrantedDocument() {
        Tenant tenantA = persistTenant("tenant-a-group-grant");
        User admin = persistUser(tenantA, User.Role.ADMIN);
        User user = persistUser(tenantA, User.Role.USER);

        Document granted = persistDocument(tenantA, "product-x", "1.0", "Group Granted Doc");
        Document ungranted = persistDocument(tenantA, "product-x", "1.0", "Group Ungranted Doc");
        persistChunk(granted.getId(), "apple pie recipe", unitVector());
        persistChunk(ungranted.getId(), "banana bread recipe", unitVector());

        GroupService.GroupDTO group = groupService.create("Support Team", tenantA.getId(), admin.getId());
        groupService.addMember(UUID.fromString(group.id()), user.getId(), tenantA.getId());
        groupDocumentAccessService.grant(granted.getId(), UUID.fromString(group.id()), tenantA.getId(), admin.getId());
        stubEmbedding(unitVector());

        SearchScope scope = documentAccessPolicy.resolveScope(user.getId(), tenantA.getId());
        assertThat(scope.documentIds()).containsExactly(granted.getId());

        List<RetrievedChunk> results = vectorSearchService.search("recipe", scope);
        assertThat(results).hasSize(1);
        assertThat(results.get(0).getDocumentName()).isEqualTo("Group Granted Doc");
    }

    @Test
    void directGrantAndGroupGrant_areUnioned_notDuplicated() {
        Tenant tenantA = persistTenant("tenant-a-union");
        User admin = persistUser(tenantA, User.Role.ADMIN);
        User user = persistUser(tenantA, User.Role.USER);

        Document viaDirectGrant = persistDocument(tenantA, "product-x", "1.0", "Direct Grant Doc");
        Document viaGroupGrant = persistDocument(tenantA, "product-x", "1.0", "Group Grant Doc");
        Document viaBoth = persistDocument(tenantA, "product-x", "1.0", "Both Doc");

        documentAccessService.grant(viaDirectGrant.getId(), user.getId(), tenantA.getId(), admin.getId());
        documentAccessService.grant(viaBoth.getId(), user.getId(), tenantA.getId(), admin.getId());

        GroupService.GroupDTO group = groupService.create("Union Team", tenantA.getId(), admin.getId());
        groupService.addMember(UUID.fromString(group.id()), user.getId(), tenantA.getId());
        groupDocumentAccessService.grant(viaGroupGrant.getId(), UUID.fromString(group.id()), tenantA.getId(), admin.getId());
        groupDocumentAccessService.grant(viaBoth.getId(), UUID.fromString(group.id()), tenantA.getId(), admin.getId());

        SearchScope scope = documentAccessPolicy.resolveScope(user.getId(), tenantA.getId());
        assertThat(scope.documentIds()).containsExactlyInAnyOrder(
            viaDirectGrant.getId(), viaGroupGrant.getId(), viaBoth.getId());
    }

    @Test
    void removingUserFromGroup_revokesTheirAccessViaThatGroup() {
        Tenant tenantA = persistTenant("tenant-a-group-remove");
        User admin = persistUser(tenantA, User.Role.ADMIN);
        User user = persistUser(tenantA, User.Role.USER);
        Document doc = persistDocument(tenantA, "product-x", "1.0", "Doc1");

        GroupService.GroupDTO group = groupService.create("Temp Team", tenantA.getId(), admin.getId());
        UUID groupId = UUID.fromString(group.id());
        groupService.addMember(groupId, user.getId(), tenantA.getId());
        groupDocumentAccessService.grant(doc.getId(), groupId, tenantA.getId(), admin.getId());

        assertThat(documentAccessPolicy.resolveScope(user.getId(), tenantA.getId()).documentIds())
            .containsExactly(doc.getId());

        groupService.removeMember(groupId, user.getId(), tenantA.getId());

        assertThat(documentAccessPolicy.resolveScope(user.getId(), tenantA.getId()).isEmpty()).isTrue();
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

    private Document persistDocument(Tenant tenant, String product, String version, String name) {
        Document doc = Document.builder()
            .id(UUID.randomUUID())
            .tenantId(tenant.getId())
            .product(product)
            .version(version)
            .documentName(name)
            .status("COMPLETED")
            .build();
        return documentRepository.save(doc);
    }

    private void persistChunk(UUID documentId, String content, float[] vector) {
        DocumentChunk chunk = new DocumentChunk();
        chunk.setId(UUID.randomUUID());
        chunk.setDocumentId(documentId);
        chunk.setChunkIndex(0);
        chunk.setContent(content);
        chunk.setEmbedding(new com.pgvector.PGvector(vector));
        chunkRepository.save(chunk);
    }

    private void stubEmbedding(float[] vector) {
        Embedding embedding = new Embedding(vector, 0);
        EmbeddingResponse response = new EmbeddingResponse(List.of(embedding));
        when(embeddingModel.call(any(EmbeddingRequest.class))).thenReturn(response);
    }

    private static float[] unitVector() {
        float[] v = new float[DIMS];
        v[0] = 1.0f;
        return v;
    }
}

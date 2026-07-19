package com.docai.bot.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;
import org.springframework.ai.embedding.Embedding;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.embedding.EmbeddingResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;

import com.docai.bot.PostgresTestContainerBase;
import com.docai.bot.application.service.VectorSearchService;
import com.docai.bot.domain.entity.Document;
import com.docai.bot.domain.entity.DocumentChunk;
import com.docai.bot.domain.entity.Tenant;
import com.docai.bot.domain.model.RetrievedChunk;
import com.docai.bot.domain.model.SearchScope;
import com.docai.bot.domain.repository.DocumentChunkRepository;
import com.docai.bot.domain.repository.DocumentRepository;
import com.docai.bot.domain.repository.TenantRepository;

/**
 * Integration test: VectorSearchService against a real PostgreSQL + pgvector container,
 * focused on the optional product/version narrowing a {@link SearchScope} can carry
 * ({@link SearchScope#withVersionNarrow}) — the access-gate side of the scope is covered
 * separately by {@link DocumentAccessIsolationTest}. The EmbeddingModel is mocked to return
 * a fixed unit vector so results are predictable.
 */
@Transactional
class VectorSearchServiceIntegrationTest extends PostgresTestContainerBase {

    @Autowired VectorSearchService vectorSearchService;
    @Autowired DocumentRepository documentRepository;
    @Autowired DocumentChunkRepository chunkRepository;
    @Autowired TenantRepository tenantRepository;
    @MockitoBean EmbeddingModel embeddingModel;

    private static final int DIMS = 1536;

    @Test
    void search_noChunksInDb_returnsEmptyList() {
        stubEmbedding(unitVector());
        Tenant tenant = persistTenant("tenant-empty");

        SearchScope scope = new SearchScope(tenant.getId(), Set.of(UUID.randomUUID()));
        List<RetrievedChunk> results = vectorSearchService.search("anything", scope);

        assertThat(results).isEmpty();
    }

    @Test
    void search_withMatchingChunk_returnsResult() {
        float[] vec = unitVector();
        stubEmbedding(vec);
        Tenant tenant = persistTenant("tenant-match");
        Document doc = persistDocument(tenant, "product-a", "1.0", "test-doc.pdf");
        persistChunk(doc.getId(), "The quick brown fox", vec);

        SearchScope scope = new SearchScope(tenant.getId(), Set.of(doc.getId()));
        List<RetrievedChunk> results = vectorSearchService.search("fox", scope);

        assertThat(results).hasSize(1);
        assertThat(results.get(0).getProduct()).isEqualTo("product-a");
        assertThat(results.get(0).getVersion()).isEqualTo("1.0");
        assertThat(results.get(0).getSimilarity()).isGreaterThan(0.0);
    }

    @Test
    void search_versionNarrowToDifferentProduct_returnsEmpty() {
        float[] vec = unitVector();
        stubEmbedding(vec);
        Tenant tenant = persistTenant("tenant-narrow-miss");
        Document doc = persistDocument(tenant, "product-b", "2.0", "doc-b.pdf");
        persistChunk(doc.getId(), "Some content", vec);

        SearchScope scope = new SearchScope(tenant.getId(), Set.of(doc.getId()))
            .withVersionNarrow("product-a", "1.0");
        List<RetrievedChunk> results = vectorSearchService.search("content", scope);

        assertThat(results).isEmpty();
    }

    @Test
    void search_versionNarrow_onlyShrinksTheAccessibleSet() {
        float[] vec = unitVector();
        stubEmbedding(vec);
        Tenant tenant = persistTenant("tenant-narrow-hit");
        Document docA = persistDocument(tenant, "product-a", "1.0", "doc-a.pdf");
        Document docB = persistDocument(tenant, "product-b", "2.0", "doc-b.pdf");
        persistChunk(docA.getId(), "Content A", vec);
        persistChunk(docB.getId(), "Content B", vec);

        SearchScope scope = new SearchScope(tenant.getId(), Set.of(docA.getId(), docB.getId()))
            .withVersionNarrow("product-a", "1.0");
        List<RetrievedChunk> results = vectorSearchService.search("content", scope);

        assertThat(results).extracting(RetrievedChunk::getProduct).containsExactly("product-a");
    }

    @Test
    void search_withoutNarrow_searchesAllChunksInScope() {
        float[] vec = unitVector();
        stubEmbedding(vec);
        Tenant tenant = persistTenant("tenant-no-narrow");
        Document docA = persistDocument(tenant, "product-a", "1.0", "doc-a.pdf");
        Document docB = persistDocument(tenant, "product-b", "2.0", "doc-b.pdf");
        persistChunk(docA.getId(), "Content A", vec);
        persistChunk(docB.getId(), "Content B", vec);

        SearchScope scope = new SearchScope(tenant.getId(), Set.of(docA.getId(), docB.getId()));
        List<RetrievedChunk> results = vectorSearchService.search("content", scope);

        assertThat(results.stream().map(RetrievedChunk::getProduct).collect(Collectors.toSet()))
            .containsExactlyInAnyOrder("product-a", "product-b");
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private Tenant persistTenant(String slug) {
        return tenantRepository.save(Tenant.builder().name(slug).slug(slug).build());
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

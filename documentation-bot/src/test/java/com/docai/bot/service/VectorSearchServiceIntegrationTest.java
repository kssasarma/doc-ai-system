package com.docai.bot.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.embedding.EmbeddingResponse;
import org.springframework.ai.embedding.Embedding;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;

import com.docai.bot.PostgresTestContainerBase;
import com.docai.bot.application.service.VectorSearchService;
import com.docai.bot.domain.model.RetrievedChunk;
import com.docai.bot.domain.repository.DocumentChunkRepository;
import com.docai.bot.domain.entity.DocumentChunk;

/**
 * Integration test: VectorSearchService against a real PostgreSQL + pgvector container.
 * The EmbeddingModel is mocked to return a fixed unit vector so results are predictable.
 */
@Transactional
class VectorSearchServiceIntegrationTest extends PostgresTestContainerBase {

    @Autowired VectorSearchService vectorSearchService;
    @Autowired DocumentChunkRepository chunkRepository;
    @MockitoBean EmbeddingModel embeddingModel;

    private static final int DIMS = 1536;

    @Test
    void search_noChunksInDb_returnsEmptyList() {
        stubEmbedding(unitVector());

        List<RetrievedChunk> results = vectorSearchService.search("anything", "product-a", "1.0");

        assertThat(results).isEmpty();
    }

    @Test
    void search_withMatchingChunk_returnsResult() {
        float[] vec = unitVector();
        stubEmbedding(vec);
        persistChunk("product-a", "1.0", "The quick brown fox", vec);

        List<RetrievedChunk> results = vectorSearchService.search("fox", "product-a", "1.0");

        assertThat(results).hasSize(1);
        assertThat(results.get(0).getProduct()).isEqualTo("product-a");
        assertThat(results.get(0).getVersion()).isEqualTo("1.0");
        assertThat(results.get(0).getSimilarity()).isGreaterThan(0.0);
    }

    @Test
    void search_differentProduct_returnsEmpty() {
        float[] vec = unitVector();
        stubEmbedding(vec);
        persistChunk("product-b", "2.0", "Some content", vec);

        List<RetrievedChunk> results = vectorSearchService.search("content", "product-a", "1.0");

        assertThat(results).isEmpty();
    }

    @Test
    void search_withNullProductAndVersion_searchesAllChunks() {
        float[] vec = unitVector();
        stubEmbedding(vec);
        persistChunk("product-a", "1.0", "Content A", vec);
        persistChunk("product-b", "2.0", "Content B", vec);

        List<RetrievedChunk> results = vectorSearchService.search("content", null, null);

        assertThat(results).hasSizeGreaterThanOrEqualTo(2);
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private void stubEmbedding(float[] vector) {
        Embedding embedding = new Embedding(vector, 0);
        EmbeddingResponse response = new EmbeddingResponse(List.of(embedding));
        when(embeddingModel.call(any(EmbeddingRequest.class))).thenReturn(response);
    }

    private void persistChunk(String product, String version, String content, float[] vector) {
        DocumentChunk chunk = new DocumentChunk();
        chunk.setDocumentId(UUID.randomUUID());
        chunk.setDocumentName("test-doc.pdf");
        chunk.setProduct(product);
        chunk.setVersion(version);
        chunk.setContent(content);
        chunk.setChunkIndex(0);
        chunk.setEmbedding(new com.pgvector.PGvector(vector));
        chunkRepository.save(chunk);
    }

    private static float[] unitVector() {
        float[] v = new float[DIMS];
        v[0] = 1.0f;
        return v;
    }
}

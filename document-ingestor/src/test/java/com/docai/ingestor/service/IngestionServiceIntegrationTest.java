package com.docai.ingestor.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.io.File;
import java.io.FileWriter;
import java.nio.file.Files;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.ai.embedding.Embedding;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.embedding.EmbeddingResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import static org.mockito.Mockito.mock;

import com.docai.ingestor.PostgresTestContainerBase;
import com.docai.ingestor.application.service.IngestionService;
import com.docai.ingestor.application.service.SemanticChunker;
import com.docai.ingestor.domain.entity.Document;
import com.docai.ingestor.domain.entity.Document.IngestionStatus;
import com.docai.ingestor.domain.model.SemanticChunk;
import com.docai.ingestor.domain.repository.DocumentChunkRepository;
import com.docai.ingestor.domain.repository.DocumentRepository;

class IngestionServiceIntegrationTest extends PostgresTestContainerBase {

    @Autowired IngestionService ingestionService;
    @Autowired DocumentRepository documentRepository;
    @Autowired DocumentChunkRepository chunkRepository;

    @MockitoBean EmbeddingModel embeddingModel;
    @MockitoBean SemanticChunker semanticChunker;

    // ── calculateFileHash ─────────────────────────────────────────────────────

    @Test
    void calculateFileHash_sameContent_returnsSameHash() throws Exception {
        File f1 = createTempTextFile("deterministic content");
        File f2 = createTempTextFile("deterministic content");

        String h1 = ingestionService.calculateFileHash(f1);
        String h2 = ingestionService.calculateFileHash(f2);

        assertThat(h1).isEqualTo(h2);
        assertThat(h1).hasSize(64); // SHA-256 hex
    }

    @Test
    void calculateFileHash_differentContent_returnsDifferentHashes() throws Exception {
        File f1 = createTempTextFile("content A");
        File f2 = createTempTextFile("content B");

        assertThat(ingestionService.calculateFileHash(f1))
            .isNotEqualTo(ingestionService.calculateFileHash(f2));
    }

    // ── prepareRetrigger ──────────────────────────────────────────────────────

    @Test
    void prepareRetrigger_failedDocument_resetsToProcessing() throws Exception {
        File file = createTempTextFile("test content");
        Document doc = documentRepository.save(Document.builder()
            .product("prod").version("1.0")
            .documentName("test.txt")
            .filePath(file.getAbsolutePath())
            .fileHash(ingestionService.calculateFileHash(file))
            .fileType("txt")
            .status(IngestionStatus.FAILED)
            .errorMessage("previous error")
            .build());

        ingestionService.prepareRetrigger(doc.getId());

        Document reloaded = documentRepository.findById(doc.getId()).orElseThrow();
        assertThat(reloaded.getStatus()).isEqualTo(IngestionStatus.PROCESSING);
        assertThat(reloaded.getErrorMessage()).isNull();
    }

    @Test
    void prepareRetrigger_completedDocument_throwsIllegalState() {
        Document doc = documentRepository.save(Document.builder()
            .product("prod").version("1.0")
            .documentName("done.txt")
            .filePath("/some/path")
            .fileHash("abc123")
            .fileType("txt")
            .status(IngestionStatus.COMPLETED)
            .build());

        assertThatThrownBy(() -> ingestionService.prepareRetrigger(doc.getId()))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("already processed");
    }

    @Test
    void prepareRetrigger_unknownId_throwsIllegalArgument() {
        assertThatThrownBy(() -> ingestionService.prepareRetrigger(UUID.randomUUID()))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Document not found");
    }

    // ── ingestUploadedFile — full async pipeline ──────────────────────────────

    @Test
    void ingestUploadedFile_validFile_setsCompletedAndSavesChunks() throws Exception {
        File file = createTempTextFile("This is the document content for ingestion testing.");

        // One simple leaf chunk returned by the mocked chunker
        when(semanticChunker.chunk(any())).thenReturn(List.of(
            SemanticChunk.builder()
                .index(0).content("This is the document content for ingestion testing.")
                .tokenCount(10).isLeaf(true).build()
        ));

        // Embedding model returns a valid 1024-float vector
        when(embeddingModel.call(any(EmbeddingRequest.class))).thenReturn(fakeEmbeddingResponse());

        Document doc = documentRepository.save(Document.builder()
            .product("prod").version("2.0")
            .documentName("ingestion-test.txt")
            .filePath(file.getAbsolutePath())
            .fileHash(ingestionService.calculateFileHash(file))
            .fileType("txt")
            .status(IngestionStatus.PROCESSING)
            .build());

        ingestionService.ingestUploadedFile(doc.getId());

        // Poll for async completion (up to 3s; fast because embeddings are mocked)
        Document result = pollUntilNotProcessing(doc.getId(), 30, 100);

        assertThat(result.getStatus()).isEqualTo(IngestionStatus.COMPLETED);
        assertThat(result.getChunkCount()).isEqualTo(1);
        assertThat(chunkRepository.findByDocumentIdOrderByChunkIndex(doc.getId())).hasSize(1);
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private Document pollUntilNotProcessing(UUID id, int maxAttempts, long intervalMs)
            throws InterruptedException {
        Document doc = null;
        for (int i = 0; i < maxAttempts; i++) {
            Thread.sleep(intervalMs);
            doc = documentRepository.findById(id).orElseThrow();
            if (doc.getStatus() != IngestionStatus.PROCESSING) break;
        }
        return doc;
    }

    private static File createTempTextFile(String content) throws Exception {
        File f = Files.createTempFile("ingestor-test-", ".txt").toFile();
        f.deleteOnExit();
        try (FileWriter fw = new FileWriter(f)) {
            fw.write(content);
        }
        return f;
    }

    @SuppressWarnings("unchecked")
    private static EmbeddingResponse fakeEmbeddingResponse() {
        float[] vector = new float[1024];
        for (int i = 0; i < 1024; i++) vector[i] = 0.01f;
        Embedding embedding = mock(Embedding.class);
        when(embedding.getOutput()).thenReturn(vector);
        EmbeddingResponse response = mock(EmbeddingResponse.class);
        when(response.getResults()).thenReturn(List.of(embedding));
        when(response.getResult()).thenReturn(embedding);
        return response;
    }
}

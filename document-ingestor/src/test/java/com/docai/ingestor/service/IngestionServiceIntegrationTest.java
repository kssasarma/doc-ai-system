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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import com.docai.ingestor.PostgresTestContainerBase;
import com.docai.ingestor.application.service.DocumentStorageService;
import com.docai.ingestor.application.service.EmbeddingService;
import com.docai.ingestor.application.service.IngestionService;
import com.docai.ingestor.application.service.SemanticChunker;
import com.docai.ingestor.domain.entity.Document;
import com.docai.ingestor.domain.entity.Document.IngestionStatus;
import com.docai.ingestor.domain.model.SemanticChunk;
import com.docai.ingestor.domain.repository.DocumentChunkRepository;
import com.docai.ingestor.domain.repository.DocumentRepository;

/**
 * DocumentStorageService is mocked here (no real S3/SeaweedFS in this test environment) — resolve()
 * is stubbed to hand back the same local file the test itself created, simulating "storage
 * returned a local working copy with this content" without needing a real object store.
 */
class IngestionServiceIntegrationTest extends PostgresTestContainerBase {

    @Autowired IngestionService ingestionService;
    @Autowired DocumentRepository documentRepository;
    @Autowired DocumentChunkRepository chunkRepository;

    // EmbeddingService is mocked wholesale: it now builds per-tenant clients from each tenant's
    // own AI config (no platform key/bean exists to mock), and its internals are covered by
    // EmbeddingServiceTest — this test only cares about the ingestion pipeline around it.
    @MockitoBean EmbeddingService embeddingService;
    @MockitoBean SemanticChunker semanticChunker;
    @MockitoBean DocumentStorageService documentStorageService;

    // ── prepareRetrigger ──────────────────────────────────────────────────────

    @Test
    void prepareRetrigger_failedDocument_resetsToProcessing() {
        when(documentStorageService.exists("some-key")).thenReturn(true);

        Document doc = documentRepository.save(Document.builder()
            .product("prod").version("1.0")
            .documentName("test.txt")
            .storageKey("some-key")
            .storageType("S3")
            .fileHash("abc123")
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
            .fileHash("abc123")
            .fileType("txt")
            .status(IngestionStatus.COMPLETED)
            .build());

        assertThatThrownBy(() -> ingestionService.prepareRetrigger(doc.getId()))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("already processed");
    }

    @Test
    void prepareRetrigger_missingFromStorage_throwsIllegalState() {
        when(documentStorageService.exists("gone-key")).thenReturn(false);

        Document doc = documentRepository.save(Document.builder()
            .product("prod").version("1.0")
            .documentName("test.txt")
            .storageKey("gone-key")
            .storageType("S3")
            .fileHash("abc123")
            .fileType("txt")
            .status(IngestionStatus.FAILED)
            .build());

        assertThatThrownBy(() -> ingestionService.prepareRetrigger(doc.getId()))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("Original file not found");
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
        when(documentStorageService.exists("upload-key")).thenReturn(true);
        when(documentStorageService.resolve("upload-key")).thenReturn(file.toPath());

        // One simple leaf chunk returned by the mocked chunker
        when(semanticChunker.chunk(any())).thenReturn(List.of(
            SemanticChunk.builder()
                .index(0).content("This is the document content for ingestion testing.")
                .tokenCount(10).isLeaf(true).build()
        ));

        // Embedding service returns a valid 1024-float vector for the single chunk
        when(embeddingService.generateEmbeddings(any(), any())).thenReturn(
            new EmbeddingService.TenantEmbeddingBatchResult(List.of(fakeVector()), "text-embedding-3-small"));

        Document doc = documentRepository.save(Document.builder()
            .product("prod").version("2.0")
            .documentName("ingestion-test.txt")
            .storageKey("upload-key")
            .storageType("S3")
            .fileHash("irrelevant-for-this-test")
            .fileType("txt")
            .status(IngestionStatus.PROCESSING)
            .build());

        ingestionService.ingestUploadedFile(doc.getId());

        // Poll for async completion (up to 3s; fast because embeddings are mocked)
        Document result = pollUntilNotProcessing(doc.getId(), 30, 100);

        assertThat(result.getStatus()).isEqualTo(IngestionStatus.COMPLETED);
        assertThat(result.getChunkCount()).isEqualTo(1);
        assertThat(result.getStorageKey()).isNull(); // cleared on successful completion
        assertThat(chunkRepository.findByDocumentIdOrderByChunkIndex(doc.getId())).hasSize(1);
    }

    @Test
    void ingestUploadedFile_missingFromStorage_setsFailed() {
        when(documentStorageService.exists("missing-key")).thenReturn(false);

        Document doc = documentRepository.save(Document.builder()
            .product("prod").version("1.0")
            .documentName("missing.txt")
            .storageKey("missing-key")
            .storageType("S3")
            .fileHash("abc123")
            .fileType("txt")
            .status(IngestionStatus.PROCESSING)
            .build());

        ingestionService.ingestUploadedFile(doc.getId());

        Document reloaded = documentRepository.findById(doc.getId()).orElseThrow();
        assertThat(reloaded.getStatus()).isEqualTo(IngestionStatus.FAILED);
        assertThat(reloaded.getErrorMessage()).isEqualTo("Upload file not found");
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

    private static float[] fakeVector() {
        float[] vector = new float[1024];
        for (int i = 0; i < 1024; i++) vector[i] = 0.01f;
        return vector;
    }
}

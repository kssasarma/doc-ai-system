package com.docai.ingestor.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.docai.ingestor.application.service.DocumentQuotaService;
import com.docai.ingestor.application.service.DocumentStorageService;
import com.docai.ingestor.application.service.EmbeddingService;
import com.docai.ingestor.application.service.IngestionService;
import com.docai.ingestor.application.service.PiiDetectionService;
import com.docai.ingestor.application.service.SemanticChunker;
import com.docai.ingestor.application.service.TextChunker;
import com.docai.ingestor.domain.entity.DocumentChunk;
import com.docai.ingestor.domain.repository.DocumentChunkRepository;
import com.docai.ingestor.domain.repository.DocumentRepository;
import com.docai.ingestor.domain.repository.PiiFlagRepository;

import org.springframework.context.ApplicationEventPublisher;

/** Unit coverage for the chunk-level PII rejection admin action — see PiiFlagController's
 * /chunk/{id}/exclude and /include endpoints, which delegate here. Deliberately plain Mockito
 * (no Spring context / DB) since these two methods are simple find-mutate-save operations;
 * end-to-end ingestion behavior is covered by IngestionServiceIntegrationTest. */
@ExtendWith(MockitoExtension.class)
class IngestionServiceChunkExclusionTest {

    @Mock private DocumentRepository documentRepository;
    @Mock private DocumentChunkRepository chunkRepository;
    @Mock private DocumentStorageService documentStorageService;
    @Mock private TextChunker textChunker;
    @Mock private SemanticChunker semanticChunker;
    @Mock private EmbeddingService embeddingService;
    @Mock private PiiDetectionService piiDetectionService;
    @Mock private PiiFlagRepository piiFlagRepository;
    @Mock private ApplicationEventPublisher eventPublisher;
    @Mock private DocumentQuotaService documentQuotaService;

    private IngestionService ingestionService;

    private static final UUID CHUNK_ID = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        ingestionService = new IngestionService(
            documentRepository, chunkRepository, documentStorageService, List.of(),
            textChunker, semanticChunker, embeddingService, piiDetectionService,
            piiFlagRepository, eventPublisher, documentQuotaService);
    }

    @Test
    void excludeChunk_marksChunkExcludedWithReason() {
        DocumentChunk chunk = DocumentChunk.builder().id(CHUNK_ID).build();
        when(chunkRepository.findById(CHUNK_ID)).thenReturn(Optional.of(chunk));
        when(chunkRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        DocumentChunk result = ingestionService.excludeChunk(CHUNK_ID, "PII");

        assertThat(result.isExcludedFromSearch()).isTrue();
        assertThat(result.getExclusionReason()).isEqualTo("PII");
    }

    @Test
    void excludeChunk_chunkNotFound_throws() {
        when(chunkRepository.findById(CHUNK_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> ingestionService.excludeChunk(CHUNK_ID, "PII"))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void includeChunk_reinstatesPreviouslyExcludedChunk() {
        DocumentChunk chunk = DocumentChunk.builder().id(CHUNK_ID)
            .excludedFromSearch(true).exclusionReason("PII").build();
        when(chunkRepository.findById(CHUNK_ID)).thenReturn(Optional.of(chunk));
        when(chunkRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        DocumentChunk result = ingestionService.includeChunk(CHUNK_ID);

        assertThat(result.isExcludedFromSearch()).isFalse();
        assertThat(result.getExclusionReason()).isNull();
    }
}

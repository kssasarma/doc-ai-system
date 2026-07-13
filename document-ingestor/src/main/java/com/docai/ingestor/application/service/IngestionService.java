package com.docai.ingestor.application.service;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.docai.ingestor.domain.entity.Document;
import com.docai.ingestor.domain.entity.Document.IngestionStatus;
import com.docai.ingestor.domain.entity.DocumentChunk;
import com.docai.ingestor.domain.model.SemanticChunk;
import com.docai.ingestor.domain.repository.DocumentChunkRepository;
import com.docai.ingestor.domain.repository.DocumentRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class IngestionService {

    private final DocumentRepository documentRepository;
    private final DocumentChunkRepository chunkRepository;
    private final DocumentStorageService documentStorageService;
    private final List<DocumentParser> parsers;
    private final TextChunker textChunker;
    private final SemanticChunker semanticChunker;
    private final EmbeddingService embeddingService;
    private final PiiDetectionService piiDetectionService;

    /**
     * Ingest an uploaded file with explicit metadata. Downloads a working copy from storage for
     * Apache Tika to read, always cleans that local copy up (success or failure), and — on
     * success only — deletes the authoritative copy from storage too (see {@link #processDocument}).
     */
    @Async
    @Transactional
    public void ingestUploadedFile(UUID documentId) {
        Document document = documentRepository.findById(documentId)
            .orElseThrow(() -> new IllegalArgumentException("Document not found: " + documentId));

        String storageKey = document.getStorageKey();
        if (storageKey == null || !documentStorageService.exists(storageKey)) {
            log.error("Upload file not found in storage for document: {}", documentId);
            document.setStatus(IngestionStatus.FAILED);
            document.setErrorMessage("Upload file not found");
            documentRepository.save(document);
            return;
        }

        Path localCopy = documentStorageService.resolve(storageKey);
        try {
            processDocument(document, localCopy.toFile());
        } catch (Exception e) {
            log.error("Failed to process uploaded document: {}", document.getDocumentName(), e);
            document.setStatus(IngestionStatus.FAILED);
            document.setErrorMessage(e.getMessage());
            documentRepository.save(document);
        } finally {
            deleteQuietly(localCopy);
        }
    }

    /**
     * Validate and reset a document to PROCESSING state for retrigger.
     * The caller is responsible for invoking ingestUploadedFile after this method returns
     * so that the transaction commits before the async task reads the document.
     */
    @Transactional
    public void prepareRetrigger(UUID documentId) {
        Document doc = documentRepository.findById(documentId)
            .orElseThrow(() -> new IllegalArgumentException("Document not found: " + documentId));

        if (doc.getStatus() == IngestionStatus.COMPLETED) {
            throw new IllegalStateException(
                "Document already processed. Original file has been deleted. Please re-upload.");
        }

        String storageKey = doc.getStorageKey();
        if (storageKey == null || !documentStorageService.exists(storageKey)) {
            throw new IllegalStateException(
                "Original file not found. Please re-upload the document.");
        }

        chunkRepository.deleteByDocumentId(documentId);
        doc.setStatus(IngestionStatus.PROCESSING);
        doc.setErrorMessage(null);
        documentRepository.save(doc);
    }

    private void deleteQuietly(Path path) {
        try {
            Files.deleteIfExists(path);
        } catch (Exception e) {
            log.warn("Could not delete working copy {}: {}", path, e.getMessage());
        }
    }

    private void processDocument(Document document, File file) throws Exception {
        String fileType = document.getFileType();
        String content = parseDocument(file, fileType);

        if (content == null || content.isBlank()) {
            throw new IllegalStateException("Parsed content is empty for: " + file.getName());
        }

        try {
            boolean needsReview = piiDetectionService.scanAndFlag(document.getId(), document.getTenantId(), content);
            if (needsReview) {
                log.warn("Document {} flagged for PII review (HIGH/CRITICAL match)", document.getId());
            }
        } catch (Exception e) {
            // Never fail ingestion over the PII scan itself — flagging is best-effort.
            log.warn("PII scan failed for document {}: {}", document.getId(), e.getMessage());
        }

        List<SemanticChunk> semanticChunks = semanticChunker.chunk(content);

        // Two-pass: first save all chunks to get their DB IDs, then update parent references.
        // We use a list to track saved entities by chunk index for parent-linking.
        DocumentChunk[] savedChunks = new DocumentChunk[semanticChunks.size()];

        for (SemanticChunk sc : semanticChunks) {
            log.debug("Embedding chunk {} ({}) of {}", sc.getIndex(), sc.getChunkType(), file.getName());
            float[] embedding = embeddingService.generateEmbedding(sc.getContent());

            DocumentChunk chunk = DocumentChunk.builder()
                .documentId(document.getId())
                .chunkIndex(sc.getIndex())
                .content(sc.getContent())
                .embedding(embedding)
                .tokenCount(sc.getTokenCount())
                .chunkType(sc.getChunkType())
                .sectionHeader(sc.getSectionHeader())
                .pageNumber(sc.getPageNumber())
                .codeLanguage(sc.getCodeLanguage())
                .isLeaf(sc.isLeaf())
                .build();

            savedChunks[sc.getIndex()] = chunkRepository.save(chunk);
        }

        // Second pass: link leaf chunks to their parent section chunk
        for (SemanticChunk sc : semanticChunks) {
            if (sc.getParentChunkIndex() != null) {
                DocumentChunk leaf   = savedChunks[sc.getIndex()];
                DocumentChunk parent = savedChunks[sc.getParentChunkIndex()];
                if (leaf != null && parent != null) {
                    leaf.setParentChunkId(parent.getId());
                    chunkRepository.save(leaf);
                }
            }
        }

        // Capture before clearing — this is the authoritative stored copy, distinct from the
        // local working copy in `file`, which the caller (ingestUploadedFile) cleans up itself.
        String storageKey = document.getStorageKey();

        document.setChunkCount(semanticChunks.size());
        document.setStatus(IngestionStatus.COMPLETED);
        document.setStorageKey(null);
        document.setStorageType(null);
        documentRepository.save(document);

        documentStorageService.delete(storageKey);
        log.info("Deleted source file from storage after ingestion: {}", storageKey);

        long leafCount = semanticChunks.stream().filter(SemanticChunk::isLeaf).count();
        log.info("Successfully ingested: {} — {} total chunks ({} leaf, {} section)",
            file.getName(), semanticChunks.size(), leafCount, semanticChunks.size() - leafCount);
    }

    private String parseDocument(File file, String fileType) throws Exception {
        DocumentParser parser = parsers.stream()
            .filter(p -> p.supports(fileType))
            .findFirst()
            .orElseThrow(() -> new IllegalArgumentException("No parser for file type: " + fileType));
        return parser.parseDocument(file);
    }

}

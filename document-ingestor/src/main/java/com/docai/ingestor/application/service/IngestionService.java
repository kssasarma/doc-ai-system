package com.docai.ingestor.application.service;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.docai.ingestor.application.event.DocumentIngestionCompletedEvent;
import com.docai.ingestor.domain.entity.Document;
import com.docai.ingestor.domain.entity.Document.IngestionStatus;
import com.docai.ingestor.domain.entity.DocumentChunk;
import com.docai.ingestor.domain.model.SemanticChunk;
import com.docai.ingestor.domain.repository.DocumentChunkRepository;
import com.docai.ingestor.domain.repository.DocumentRepository;
import com.docai.ingestor.domain.repository.PiiFlagRepository;

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
    private final PiiFlagRepository piiFlagRepository;
    private final ApplicationEventPublisher eventPublisher;

    @Value("${ingestor.stuck-processing-timeout-minutes:30}")
    private long stuckProcessingTimeoutMinutes;

    /**
     * Ingest an uploaded file with explicit metadata. Downloads a working copy from storage for
     * Apache Tika to read, always cleans that local copy up (success or failure).
     *
     * Deliberately NOT {@code @Transactional} at this level: parsing, chunking, and embedding are
     * slow, network-bound operations with no database writes of their own (aside from the PII
     * scan, which is independently transactional) — holding a DB connection open for their entire
     * duration serializes unrelated work against the connection pool for no reason. The only
     * database writes are the short, all-or-nothing {@link #completeIngestion} /
     * {@link #markFailed} calls at the end, each its own transaction. This also closes a real bug:
     * previously the whole method was one {@code @Transactional}, so a failure partway through
     * embedding (chunk 500 of 1000) still committed chunks 0..499 alongside a FAILED status —
     * because retrieval has no status filter, those orphaned chunks were returned in search
     * results for a document the UI showed as failed.
     */
    @Async
    public void ingestUploadedFile(UUID documentId) {
        MDC.put("documentId", documentId.toString());
        try {
            Document document = documentRepository.findById(documentId)
                .orElseThrow(() -> new IllegalArgumentException("Document not found: " + documentId));

            String storageKey = document.getStorageKey();
            if (storageKey == null || !documentStorageService.exists(storageKey)) {
                log.error("Upload file not found in storage for document: {}", documentId);
                markFailed(documentId, "Upload file not found");
                return;
            }

            Path localCopy = documentStorageService.resolve(storageKey);
            try {
                ParseResult result = parseChunkAndEmbed(document, localCopy.toFile());
                completeIngestion(documentId, result.chunks(), result.embeddingModel());
            } catch (Exception e) {
                log.error("Failed to process uploaded document: {}", document.getDocumentName(), e);
                markFailed(documentId, e.getMessage());
            } finally {
                deleteQuietly(localCopy);
            }
        } finally {
            MDC.remove("documentId");
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
                "Document already processed. Re-processing a completed document isn't supported — re-upload to ingest a new version.");
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

    /**
     * Recovers documents stuck in PROCESSING — e.g. the pod died mid-ingestion, or the async
     * executor's thread was killed — which would otherwise stay PROCESSING forever, since nothing
     * else ever revisits them. Marking them FAILED makes them eligible for the existing per-
     * document retrigger and bulk reprocess-failed actions; it does not retry them itself.
     */
    @Scheduled(fixedDelayString = "${ingestor.stuck-processing-check-interval-ms:300000}")
    public void reapStuckProcessingDocuments() {
        LocalDateTime cutoff = LocalDateTime.now().minusMinutes(stuckProcessingTimeoutMinutes);
        List<Document> stuck = documentRepository.findByStatusAndUpdatedAtBefore(IngestionStatus.PROCESSING, cutoff);
        for (Document doc : stuck) {
            log.warn("Reaping document {} ({}) stuck in PROCESSING since {} — marking FAILED so it can be retriggered",
                doc.getId(), doc.getDocumentName(), doc.getUpdatedAt());
            markFailed(doc.getId(), "Ingestion timed out or was interrupted (stuck in PROCESSING for over "
                + stuckProcessingTimeoutMinutes + " minutes) — use retrigger to try again");
        }
        if (!stuck.isEmpty()) {
            log.info("Stuck-PROCESSING reaper: recovered {} document(s)", stuck.size());
        }
    }

    @Transactional
    public void markFailed(UUID documentId, String errorMessage) {
        documentRepository.findById(documentId).ifPresent(doc -> {
            doc.setStatus(IngestionStatus.FAILED);
            doc.setErrorMessage(errorMessage);
            documentRepository.save(doc);
            eventPublisher.publishEvent(new DocumentIngestionCompletedEvent(
                doc.getId(), doc.getTenantId(), false, errorMessage));
        });
    }

    private void deleteQuietly(Path path) {
        try {
            Files.deleteIfExists(path);
        } catch (Exception e) {
            log.warn("Could not delete working copy {}: {}", path, e.getMessage());
        }
    }

    private record ParseResult(List<SemanticChunk> chunks, String embeddingModel) {}

    /** Parses, chunks, PII-scans, and embeds — no chunk/document writes. Throws on any failure;
     * the caller is responsible for marking the document FAILED. */
    private ParseResult parseChunkAndEmbed(Document document, File file) throws Exception {
        String fileType = document.getFileType();
        String content = parseDocument(file, fileType);

        if (content == null || content.isBlank()) {
            throw new IllegalStateException("Parsed content is empty for: " + file.getName());
        }

        try {
            boolean hasCriticalPii = piiDetectionService.scanAndFlag(document.getId(), document.getTenantId(), content);
            if (hasCriticalPii) {
                log.warn("Document {} has CRITICAL PII matches — will be quarantined pending admin review", document.getId());
            }
        } catch (Exception e) {
            // Never fail ingestion over the PII scan itself — flagging is best-effort.
            log.warn("PII scan failed for document {}: {}", document.getId(), e.getMessage());
        }

        List<SemanticChunk> semanticChunks = semanticChunker.chunk(content);
        log.debug("Embedding {} chunks of {}", semanticChunks.size(), file.getName());
        List<String> chunkTexts = semanticChunks.stream().map(SemanticChunk::getContent).toList();
        EmbeddingService.TenantEmbeddingBatchResult batchResult =
            embeddingService.generateEmbeddings(chunkTexts, document.getTenantId());
        for (int i = 0; i < semanticChunks.size(); i++) {
            semanticChunks.get(i).setEmbedding(batchResult.embeddings().get(i));
        }
        return new ParseResult(semanticChunks, batchResult.modelUsed());
    }

    /**
     * The single all-or-nothing write: persists every chunk, flips the document to COMPLETED, and
     * — since this document just became the authoritative COMPLETED copy for its (tenant, product,
     * version) — retires any other COMPLETED document sharing that same triple (see class javadoc
     * on why a naive re-upload otherwise leaves stale, duplicate content permanently searchable).
     * A prior document being retired here still had its storage key cleared when it originally
     * completed (see below), so there is no stored object left to clean up for it — only its DB
     * rows. Known gap: bot-owned DocumentAccess/GroupDocumentAccess grants reference the old
     * document's id with no FK (cross-service, no shared migration ordering) and are not migrated
     * to the new document here — that requires the ingestion-completed event (a separate, larger
     * change) so the bot can react; until then, a superseded document's grants are simply orphaned
     * (inert), not reassigned, and whoever had access must be re-granted access to the new upload.
     */
    @Transactional
    public void completeIngestion(UUID documentId, List<SemanticChunk> semanticChunks, String embeddingModel) {
        Document document = documentRepository.findById(documentId)
            .orElseThrow(() -> new IllegalArgumentException("Document not found: " + documentId));
        document.setEmbeddingModel(embeddingModel);

        DocumentChunk[] savedChunks = new DocumentChunk[semanticChunks.size()];
        for (SemanticChunk sc : semanticChunks) {
            DocumentChunk chunk = DocumentChunk.builder()
                .documentId(document.getId())
                .chunkIndex(sc.getIndex())
                .content(sc.getContent())
                .embedding(sc.getEmbedding())
                .tokenCount(sc.getTokenCount())
                .chunkType(sc.getChunkType())
                .sectionHeader(sc.getSectionHeader())
                .pageNumber(sc.getPageNumber())
                .codeLanguage(sc.getCodeLanguage())
                .isLeaf(sc.isLeaf())
                .build();
            savedChunks[sc.getIndex()] = chunkRepository.save(chunk);
        }

        // Second pass: link leaf chunks to their parent section chunk (needs the parent's
        // DB-assigned id from the first pass).
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

        // CRITICAL PII (SSN, credit card, AWS keys — see PiiDetectionService) holds the document
        // back from search pending admin review, rather than letting it go straight to COMPLETED
        // and searchable. The chunks/embeddings are still persisted below (so review has the
        // extracted content to look at, and release is just a status flip — not a re-ingest).
        boolean hasCriticalPii = piiFlagRepository.findByDocumentId(documentId).stream()
            .anyMatch(f -> "CRITICAL".equals(f.getRiskLevel()));

        document.setChunkCount(semanticChunks.size());
        document.setStatus(hasCriticalPii ? IngestionStatus.QUARANTINED : IngestionStatus.COMPLETED);
        documentRepository.save(document);

        // Storage object (and storageKey) is deliberately kept, not deleted, on success — it's
        // the source citations open (see InternalDocumentController) rather than a disposable
        // working copy. It's cleaned up later only if this document is superseded or rejected
        // (see retireSupersededDocuments / rejectQuarantined).
        if (hasCriticalPii) {
            log.warn("Document {} quarantined pending PII review — not searchable until an admin releases it",
                documentId);
        } else {
            // Only a COMPLETED document is the new authoritative copy for its (tenant, product,
            // version) — a QUARANTINED one is not, so nothing else should be retired on its behalf.
            retireSupersededDocuments(document);
        }

        long leafCount = semanticChunks.stream().filter(SemanticChunk::isLeaf).count();
        log.info("Successfully ingested: {} — {} total chunks ({} leaf, {} section)",
            document.getDocumentName(), semanticChunks.size(), leafCount, semanticChunks.size() - leafCount);

        eventPublisher.publishEvent(new DocumentIngestionCompletedEvent(
            document.getId(), document.getTenantId(), true, null));
    }

    /** Admin approves a quarantined document's PII findings — makes it searchable, exactly as if
     * it had completed ingestion normally (including retiring any older COMPLETED document for
     * the same tenant/product/version, since this is now the authoritative copy). See
     * {@link com.docai.ingestor.adapter.rest.PiiFlagController}. */
    @Transactional
    public void releaseFromQuarantine(UUID documentId) {
        Document document = documentRepository.findById(documentId)
            .orElseThrow(() -> new IllegalArgumentException("Document not found: " + documentId));
        if (document.getStatus() != IngestionStatus.QUARANTINED) {
            throw new IllegalStateException("Document is not quarantined: " + documentId);
        }
        document.setStatus(IngestionStatus.COMPLETED);
        documentRepository.save(document);
        retireSupersededDocuments(document);
        log.info("Released document {} from PII quarantine", documentId);
    }

    /** Admin rejects a quarantined document — deletes it outright (document_chunks and pii_flags
     * both cascade-delete at the DB level) plus its stored source file, which (unlike a normal
     * completed ingestion) is still present since ingestion never deletes it on success. */
    @Transactional
    public void rejectQuarantined(UUID documentId) {
        Document document = documentRepository.findById(documentId)
            .orElseThrow(() -> new IllegalArgumentException("Document not found: " + documentId));
        if (document.getStatus() != IngestionStatus.QUARANTINED) {
            throw new IllegalStateException("Document is not quarantined: " + documentId);
        }
        if (document.getStorageKey() != null) {
            documentStorageService.delete(document.getStorageKey());
        }
        documentRepository.delete(document);
        log.info("Rejected and deleted quarantined document {}", documentId);
    }

    /** Admin-initiated delete from the documents admin table (Phase 6.4) — any status, not just
     * quarantined. Same cleanup shape as {@link #rejectQuarantined}: chunks cascade at the DB
     * level too, but deleted explicitly first for the same reasoning as the rest of this class. */
    @Transactional
    public void deleteDocument(UUID documentId, UUID tenantId) {
        Document document = documentRepository.findById(documentId)
            .filter(d -> tenantId.equals(d.getTenantId()))
            .orElseThrow(() -> new IllegalArgumentException("Document not found: " + documentId));
        if (document.getStorageKey() != null) {
            documentStorageService.delete(document.getStorageKey());
        }
        chunkRepository.deleteByDocumentId(documentId);
        documentRepository.delete(document);
        log.info("Deleted document {} ({} v{}) for tenant {}",
            documentId, document.getProduct(), document.getVersion(), tenantId);
    }

    private void retireSupersededDocuments(Document newDocument) {
        List<Document> superseded = documentRepository.findByTenantIdAndProductAndVersionAndStatusAndIdNot(
            newDocument.getTenantId(), newDocument.getProduct(), newDocument.getVersion(),
            IngestionStatus.COMPLETED, newDocument.getId());
        for (Document old : superseded) {
            if (old.getStorageKey() != null) {
                documentStorageService.delete(old.getStorageKey());
            }
            chunkRepository.deleteByDocumentId(old.getId());
            documentRepository.delete(old);
            log.info("Superseded document {} ({} v{}) — replaced by newly-completed document {}",
                old.getId(), old.getProduct(), old.getVersion(), newDocument.getId());
        }
    }

    private String parseDocument(File file, String fileType) throws Exception {
        DocumentParser parser = parsers.stream()
            .filter(p -> p.supports(fileType))
            .findFirst()
            .orElseThrow(() -> new IllegalArgumentException("No parser for file type: " + fileType));
        return parser.parseDocument(file);
    }

}

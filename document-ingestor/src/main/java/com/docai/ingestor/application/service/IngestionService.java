package com.docai.ingestor.application.service;

import java.io.File;
import java.io.FileInputStream;
import java.security.MessageDigest;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.docai.ingestor.domain.entity.Document;
import com.docai.ingestor.domain.entity.Document.IngestionStatus;
import com.docai.ingestor.domain.entity.DocumentChunk;
import com.docai.ingestor.domain.model.FileMetadata;
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
    private final List<DocumentParser> parsers;
    private final TextChunker textChunker;
    private final SemanticChunker semanticChunker;
    private final EmbeddingService embeddingService;

    /**
     * Ingest a file from the watched directory. Skips if already ingested with same hash.
     * Deletes the source file after successful ingestion.
     */
    @Async
    @Transactional
    public void ingestDocument(File file) {
        log.info("Starting ingestion for file: {}", file.getName());

        try {
            String fileHash = calculateFileHash(file);

            if (documentRepository.existsByFileHashAndStatus(fileHash, IngestionStatus.COMPLETED)) {
                log.info("Document already ingested with same hash. Skipping: {}", file.getName());
                return;
            }

            FileMetadata metadata = FileMetadata.fromFileName(file.getName(), file.getAbsolutePath(), fileHash);

            // Remove any failed/pending record for same hash so we start fresh
            documentRepository.findByFileHash(fileHash).ifPresent(existing -> {
                chunkRepository.deleteByDocumentId(existing.getId());
                documentRepository.delete(existing);
            });

            Document document = Document.builder()
                .tenantId(Document.DEFAULT_TENANT_ID)
                .product(metadata.getProduct())
                .version(metadata.getVersion())
                .documentName(metadata.getDocumentName())
                .filePath(metadata.getFilePath())
                .fileHash(fileHash)
                .fileType(metadata.getFileType())
                .status(IngestionStatus.PROCESSING)
                .build();

            document = documentRepository.save(document);
            processDocument(document, file);

        } catch (Exception e) {
            log.error("Failed to ingest document: {}", file.getName(), e);
            updateErrorStatus(file, e);
        }
    }

    /**
     * Ingest an uploaded file with explicit metadata. Deletes the temp file after success.
     */
    @Async
    @Transactional
    public void ingestUploadedFile(UUID documentId) {
        Document document = documentRepository.findById(documentId)
            .orElseThrow(() -> new IllegalArgumentException("Document not found: " + documentId));

        File file = new File(document.getFilePath());
        if (!file.exists()) {
            log.error("Upload file not found at path: {}", document.getFilePath());
            document.setStatus(IngestionStatus.FAILED);
            document.setErrorMessage("Upload file not found");
            documentRepository.save(document);
            return;
        }

        try {
            processDocument(document, file);
        } catch (Exception e) {
            log.error("Failed to process uploaded document: {}", document.getDocumentName(), e);
            document.setStatus(IngestionStatus.FAILED);
            document.setErrorMessage(e.getMessage());
            documentRepository.save(document);
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

        String filePath = doc.getFilePath();
        if (filePath == null || !new File(filePath).exists()) {
            throw new IllegalStateException(
                "Original file not found. Please re-upload the document.");
        }

        chunkRepository.deleteByDocumentId(documentId);
        doc.setStatus(IngestionStatus.PROCESSING);
        doc.setErrorMessage(null);
        documentRepository.save(doc);
    }

    private void processDocument(Document document, File file) throws Exception {
        String fileType = document.getFileType();
        String content = parseDocument(file, fileType);

        if (content == null || content.isBlank()) {
            throw new IllegalStateException("Parsed content is empty for: " + file.getName());
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

        document.setChunkCount(semanticChunks.size());
        document.setStatus(IngestionStatus.COMPLETED);
        document.setFilePath(null);
        documentRepository.save(document);

        if (file.exists() && !file.delete()) {
            log.warn("Could not delete source file after ingestion: {}", file.getAbsolutePath());
        } else {
            log.info("Deleted source file after ingestion: {}", file.getAbsolutePath());
        }

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

    public String calculateFileHash(File file) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        try (FileInputStream fis = new FileInputStream(file)) {
            byte[] buffer = new byte[8192];
            int bytesRead;
            while ((bytesRead = fis.read(buffer)) != -1) {
                digest.update(buffer, 0, bytesRead);
            }
        }
        byte[] hashBytes = digest.digest();
        StringBuilder sb = new StringBuilder();
        for (byte b : hashBytes) {
            String hex = Integer.toHexString(0xff & b);
            if (hex.length() == 1) sb.append('0');
            sb.append(hex);
        }
        return sb.toString();
    }

    private void updateErrorStatus(File file, Exception e) {
        try {
            String fileHash = calculateFileHash(file);
            documentRepository.findByFileHash(fileHash).ifPresent(doc -> {
                doc.setStatus(IngestionStatus.FAILED);
                doc.setErrorMessage(e.getMessage());
                documentRepository.save(doc);
            });
        } catch (Exception ex) {
            log.error("Failed to update error status for: {}", file.getName(), ex);
        }
    }
}

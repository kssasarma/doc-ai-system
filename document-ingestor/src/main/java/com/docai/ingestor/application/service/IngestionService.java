package com.docai.ingestor.application.service;

import java.io.File;
import java.io.FileInputStream;
import java.security.MessageDigest;
import java.util.List;
import java.util.Optional;

import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.docai.ingestor.domain.entity.Document;
import com.docai.ingestor.domain.entity.DocumentChunk;
import com.docai.ingestor.domain.model.FileMetadata;
import com.docai.ingestor.domain.model.TextChunk;
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
	private final EmbeddingService embeddingService;

	@Async
	@Transactional
	public void ingestDocument(File file) {
		log.info("Starting ingestion for file: {}", file.getName());

		try {
			// Calculate file hash
			String fileHash = calculateFileHash(file);

			// Check if already ingested with same hash
			if (documentRepository.existsByFileHash(fileHash)) {
				log.info("Document already ingested with same hash. Skipping: {}", file.getName());
				return;
			}

			// Extract metadata from filename
			FileMetadata metadata = FileMetadata.fromFileName(file.getName(), file.getAbsolutePath(), fileHash);

			// Create document entity
			Document document = Document.builder().product(metadata.getProduct()).version(metadata.getVersion())
					.documentName(metadata.getDocumentName()).filePath(metadata.getFilePath()).fileHash(fileHash)
					.fileType(metadata.getFileType()).status(Document.IngestionStatus.PROCESSING).build();

			document = documentRepository.save(document);

			// Parse document
			String content = parseDocument(file, metadata.getFileType());

			// Chunk text
			List<TextChunk> textChunks = textChunker.chunkText(content);

			// Generate embeddings and save chunks
			for (TextChunk textChunk : textChunks) {
				log.debug("Generating embedding for chunk {} of document {}", textChunk.getIndex(), file.getName());
				float[] embedding = embeddingService.generateEmbedding(textChunk.getContent());

				log.debug("Generated embedding for chunk {} of document {}", textChunk.getIndex(), file.getName());

				log.debug("Saving chunk {} of document {}", textChunk.getIndex(), file.getName());
				DocumentChunk chunk = DocumentChunk.builder().documentId(document.getId())
						.chunkIndex(textChunk.getIndex()).content(textChunk.getContent()).embedding(embedding)
						.tokenCount(textChunk.getTokenCount()).build();

				chunkRepository.save(chunk);
				log.debug("Saved chunk {} for document {}", textChunk.getIndex(), file.getName());
			}

			// Update document status
			document.setChunkCount(textChunks.size());
			document.setStatus(Document.IngestionStatus.COMPLETED);
			documentRepository.save(document);

			log.info("Successfully ingested document: {} with {} chunks", file.getName(), textChunks.size());

		} catch (Exception e) {
			log.error("Failed to ingest document: {}", file.getName(), e);
			handleIngestionError(file, e);
		}
	}

	private String parseDocument(File file, String fileType) throws Exception {
		DocumentParser parser = parsers.stream().filter(p -> p.supports(fileType)).findFirst()
				.orElseThrow(() -> new IllegalArgumentException("No parser found for file type: " + fileType));

		return parser.parseDocument(file);
	}

	private String calculateFileHash(File file) throws Exception {
		MessageDigest digest = MessageDigest.getInstance("SHA-256");
		try (FileInputStream fis = new FileInputStream(file)) {
			byte[] buffer = new byte[8192];
			int bytesRead;
			while ((bytesRead = fis.read(buffer)) != -1) {
				digest.update(buffer, 0, bytesRead);
			}
		}

		byte[] hashBytes = digest.digest();
		StringBuilder hexString = new StringBuilder();
		for (byte b : hashBytes) {
			String hex = Integer.toHexString(0xff & b);
			if (hex.length() == 1)
				hexString.append('0');
			hexString.append(hex);
		}
		return hexString.toString();
	}

	private void handleIngestionError(File file, Exception e) {
		try {
			String fileHash = calculateFileHash(file);
			Optional<Document> existingDoc = documentRepository.findByFileHash(fileHash);

			if (existingDoc.isPresent()) {
				Document doc = existingDoc.get();
				doc.setStatus(Document.IngestionStatus.FAILED);
				doc.setErrorMessage(e.getMessage());
				documentRepository.save(doc);
			}
		} catch (Exception ex) {
			log.error("Failed to update error status for document: {}", file.getName(), ex);
		}
	}

	@Transactional
	public void reIngestDocument(File file) throws Exception {
		log.info("Re-ingesting document: {}", file.getName());

		String fileHash = calculateFileHash(file);
		Optional<Document> existing = documentRepository.findByFileHash(fileHash);

		if (existing.isPresent()) {
			Document doc = existing.get();
			chunkRepository.deleteByDocumentId(doc.getId());
			documentRepository.delete(doc);
		}

		ingestDocument(file);
	}
}

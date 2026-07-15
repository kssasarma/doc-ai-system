package com.docai.ingestor.application.event;

import java.util.UUID;

/**
 * Published by any caller (webhook, Confluence/Notion connector) whose document creation runs
 * inside its own {@code @Transactional} method — publishing this instead of calling
 * {@code IngestionService.ingestUploadedFile(...)} directly avoids a real race: that method is
 * {@code @Async} and reads the document on a separate thread/connection, which can run before the
 * publisher's transaction actually commits (READ_COMMITTED sees nothing yet) — an intermittent
 * "Document not found" that leaves the row stuck PROCESSING. {@link IngestionEventListener}
 * handles this with {@code @TransactionalEventListener(phase = AFTER_COMMIT)}, so the triggering
 * transaction is guaranteed committed before ingestion ever looks up the row.
 *
 * The plain upload path ({@code DocumentUploadController}) doesn't need this — it isn't
 * {@code @Transactional} itself, so its {@code documentRepository.save(...)} already
 * auto-commits before it calls {@code ingestUploadedFile} directly.
 */
public record DocumentIngestionRequestedEvent(UUID documentId) {
}

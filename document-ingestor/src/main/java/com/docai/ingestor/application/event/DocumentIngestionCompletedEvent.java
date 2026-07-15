package com.docai.ingestor.application.event;

import java.util.UUID;

/**
 * Published by {@code IngestionService} once a document's ingestion pipeline reaches a terminal
 * state (COMPLETED or FAILED) — the single hook for anything that needs to react to "this
 * document is now actually searchable" (or definitively isn't), rather than assuming the moment a
 * webhook/connector/upload call returns means ingestion succeeded. Current consumer:
 * {@link IngestionEventListener} updates the originating WebhookEvent's status to match. Future
 * consumers (cross-service cache invalidation / topic-subscription notification in
 * documentation-bot) should hook in here rather than re-deriving "did ingestion finish" elsewhere.
 */
public record DocumentIngestionCompletedEvent(UUID documentId, UUID tenantId, boolean success, String errorMessage) {
}

package com.docai.ingestor.application.event;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import com.docai.ingestor.application.service.IngestionService;
import com.docai.ingestor.domain.entity.WebhookEvent;
import com.docai.ingestor.domain.repository.WebhookEventRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * The three AFTER_COMMIT reactions to the ingestion lifecycle events (see the event classes'
 * javadoc for why AFTER_COMMIT specifically matters for the first one).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class IngestionEventListener {

    private static final String NOTIFY_CHANNEL = "docai_ingestion_completed";

    private final IngestionService ingestionService;
    private final WebhookEventRepository webhookEventRepository;
    private final JdbcTemplate jdbcTemplate;

    /** Starts async ingestion only once the publisher's transaction (document row creation) has
     * actually committed — fixes the webhook/connector "Document not found" race. */
    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onIngestionRequested(DocumentIngestionRequestedEvent event) {
        ingestionService.ingestUploadedFile(event.documentId());
    }

    /** Reflects the real ingestion outcome back onto whichever webhook event triggered it —
     * previously the webhook event was marked COMPLETED the moment the document row was created,
     * before ingestion (which can still fail) ever ran. */
    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onIngestionCompleted(DocumentIngestionCompletedEvent event) {
        List<WebhookEvent> pending = webhookEventRepository.findByDocumentIdAndStatus(
            event.documentId(), WebhookEvent.Status.PROCESSING);
        for (WebhookEvent webhookEvent : pending) {
            webhookEvent.setStatus(event.success() ? WebhookEvent.Status.COMPLETED : WebhookEvent.Status.FAILED);
            webhookEvent.setErrorMessage(event.success() ? null : event.errorMessage());
            webhookEvent.setProcessedAt(LocalDateTime.now());
            webhookEventRepository.save(webhookEvent);
            log.debug("Webhook event {} status synced to ingestion outcome: {}",
                webhookEvent.getId(), webhookEvent.getStatus());
        }

        // Cross-service bridge (Phase 2.4): documentation-bot LISTENs on this channel
        // (IngestionCompletedNotifyListener) to fire topic-subscription notifications once a
        // document is actually searchable — no shared queue/broker between the two services, so
        // Postgres LISTEN/NOTIFY is the mechanism. Payload is just the document id (a UUID, fixed
        // hex-dash format — safe to inline, nothing user-supplied reaches this string); the bot
        // already has its own read access to the shared `documents` table for everything else it
        // needs (tenantId/product/version/documentName/status). Only notify on success — a failed
        // ingestion has nothing new for a subscriber to be told about.
        if (event.success()) {
            jdbcTemplate.execute("NOTIFY " + NOTIFY_CHANNEL + ", '" + event.documentId() + "'");
        }
    }
}

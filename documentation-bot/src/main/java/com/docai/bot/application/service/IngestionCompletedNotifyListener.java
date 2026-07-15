package com.docai.bot.application.service;

import java.sql.Connection;
import java.sql.Statement;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import javax.sql.DataSource;

import org.postgresql.PGConnection;
import org.postgresql.PGNotification;
import org.springframework.stereotype.Component;

import com.docai.bot.config.TenantContext;
import com.docai.bot.domain.entity.Document;
import com.docai.bot.domain.repository.DocumentRepository;

import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Phase 2.4 — the bot-side half of the cross-service ingestion-completed bridge. document-ingestor
 * has no direct handle on this service (no shared queue/broker between them), so it publishes a
 * Postgres {@code NOTIFY docai_ingestion_completed, '<documentId>'} once a document reaches a
 * successful terminal state (see IngestionEventListener there); this component LISTENs for it and
 * fires {@link TopicSubscriptionService#notifySubscribersForProduct} — the only previously-unbuilt
 * piece of "new document version → subscribers get notified" (2.7's webhook status sync and 2.8's
 * frontend product-list refresh were already done).
 *
 * <p>LISTEN/NOTIFY needs one dedicated, never-returned-to-the-pool connection held open for the
 * app's lifetime (the "is listening" state is per-connection) — a small, permanent reduction in
 * the pool's available connections, acceptable at typical pool sizes (10-20). Runs its own
 * reconnect loop rather than depending on any scheduler, since it spends most of its life blocked
 * inside a JDBC driver call waiting on the socket, not on a periodic tick.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class IngestionCompletedNotifyListener {

    private static final String NOTIFY_CHANNEL = "docai_ingestion_completed";
    private static final int POLL_TIMEOUT_MS = 30_000;
    private static final int RECONNECT_DELAY_MS = 5_000;

    private final DataSource dataSource;
    private final DocumentRepository documentRepository;
    private final TopicSubscriptionService topicSubscriptionService;

    private final ExecutorService executor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "ingestion-completed-listener");
        t.setDaemon(true);
        return t;
    });
    private volatile boolean running = true;

    @jakarta.annotation.PostConstruct
    void start() {
        executor.submit(this::listenLoop);
    }

    @PreDestroy
    void stop() {
        running = false;
        executor.shutdownNow();
    }

    private void listenLoop() {
        while (running) {
            try (Connection conn = dataSource.getConnection()) {
                PGConnection pgConn = conn.unwrap(PGConnection.class);
                try (Statement stmt = conn.createStatement()) {
                    stmt.execute("LISTEN " + NOTIFY_CHANNEL);
                }
                log.info("Listening for Postgres notifications on channel '{}'", NOTIFY_CHANNEL);

                while (running) {
                    PGNotification[] notifications = pgConn.getNotifications(POLL_TIMEOUT_MS);
                    if (notifications != null) {
                        for (PGNotification n : notifications) {
                            handleNotification(n.getParameter());
                        }
                    }
                }
            } catch (Exception e) {
                if (!running) return;
                log.warn("Ingestion-completed LISTEN connection failed, reconnecting in {}ms: {}",
                    RECONNECT_DELAY_MS, e.getMessage());
                sleepQuietly();
            }
        }
    }

    private void handleNotification(String documentIdRaw) {
        try {
            UUID documentId = UUID.fromString(documentIdRaw);
            Optional<Document> doc = documentRepository.findById(documentId);
            if (doc.isEmpty() || !"COMPLETED".equals(doc.get().getStatus())) return;

            Document document = doc.get();
            TenantContext.set(document.getTenantId());
            try {
                topicSubscriptionService.notifySubscribersForProduct(
                    document.getTenantId(), document.getProduct(), document.getVersion(), document.getDocumentName());
            } finally {
                TenantContext.clear();
            }
        } catch (Exception e) {
            log.warn("Failed to process ingestion-completed notification for document {}: {}",
                documentIdRaw, e.getMessage());
        }
    }

    private void sleepQuietly() {
        try {
            Thread.sleep(RECONNECT_DELAY_MS);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
        }
    }
}

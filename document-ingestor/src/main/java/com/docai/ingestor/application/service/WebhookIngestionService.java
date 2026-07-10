package com.docai.ingestor.application.service;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Paths;
import java.security.DigestInputStream;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.docai.ingestor.config.TenantContext;
import com.docai.ingestor.domain.entity.Document;
import com.docai.ingestor.domain.entity.Document.IngestionStatus;
import com.docai.ingestor.domain.entity.WebhookEvent;
import com.docai.ingestor.domain.repository.DocumentRepository;
import com.docai.ingestor.domain.repository.WebhookEventRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class WebhookIngestionService {

    private final WebhookEventRepository webhookEventRepository;
    private final DocumentRepository documentRepository;
    private final IngestionService ingestionService;
    private final DocumentStorageService documentStorageService;

    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(30))
        .followRedirects(HttpClient.Redirect.NORMAL)
        .build();

    @Transactional
    public WebhookEvent createEvent(String downloadUrl, String product, String version,
                                    String documentName, String requestedBy) {
        WebhookEvent event = WebhookEvent.builder()
            .downloadUrl(downloadUrl)
            .product(product)
            .version(version)
            .documentName(documentName)
            .status(WebhookEvent.Status.PENDING)
            .retryCount(0)
            .requestedBy(requestedBy)
            .tenantId(TenantContext.get())
            .build();
        return webhookEventRepository.save(event);
    }

    @Async
    @Transactional
    public void processEvent(UUID eventId) {
        WebhookEvent event = webhookEventRepository.findById(eventId)
            .orElseThrow(() -> new IllegalArgumentException("Webhook event not found: " + eventId));

        event.setStatus(WebhookEvent.Status.PROCESSING);
        webhookEventRepository.save(event);

        try {
            DownloadResult downloaded = downloadAndStore(event.getDownloadUrl(), event.getTenantId());

            // Skip if already ingested — scoped to this event's tenant
            if (documentRepository.existsByFileHashAndTenantIdAndStatus(
                    downloaded.fileHash(), event.getTenantId(), IngestionStatus.COMPLETED)) {
                log.info("Webhook: document already ingested (hash match). Skipping.");
                documentStorageService.delete(downloaded.storageKey());
                event.setStatus(WebhookEvent.Status.COMPLETED);
                event.setProcessedAt(LocalDateTime.now());
                webhookEventRepository.save(event);
                return;
            }

            Optional<Document> existing = documentRepository.findByFileHashAndTenantId(downloaded.fileHash(), event.getTenantId());
            Document document;
            if (existing.isPresent()) {
                document = existing.get();
                if (document.getStorageKey() != null) {
                    // Replacing a stale (failed/pending) download's file with this fresh one.
                    documentStorageService.delete(document.getStorageKey());
                }
                document.setStorageKey(downloaded.storageKey());
                document.setStorageType(documentStorageService.storageType());
                document.setStatus(IngestionStatus.PROCESSING);
                document.setErrorMessage(null);
                document = documentRepository.save(document);
            } else {
                String docName = event.getDocumentName() != null
                    ? event.getDocumentName()
                    : deriveDocName(event.getDownloadUrl());

                document = documentRepository.save(Document.builder()
                    .tenantId(event.getTenantId())
                    .product(event.getProduct())
                    .version(event.getVersion())
                    .documentName(docName)
                    .storageKey(downloaded.storageKey())
                    .storageType(documentStorageService.storageType())
                    .fileHash(downloaded.fileHash())
                    .fileType(downloaded.extension())
                    .status(IngestionStatus.PROCESSING)
                    .build());
            }

            event.setDocumentId(document.getId());
            event.setStatus(WebhookEvent.Status.COMPLETED);
            event.setProcessedAt(LocalDateTime.now());
            webhookEventRepository.save(event);

            // Kick off the actual ingestion pipeline
            ingestionService.ingestUploadedFile(document.getId());

        } catch (Exception e) {
            log.error("Webhook ingestion failed for event {}: {}", eventId, e.getMessage(), e);
            event.setStatus(WebhookEvent.Status.FAILED);
            event.setErrorMessage(e.getMessage());
            event.setRetryCount(event.getRetryCount() + 1);
            event.setProcessedAt(LocalDateTime.now());
            webhookEventRepository.save(event);
        }
    }

    private record DownloadResult(String storageKey, String fileHash, String extension) {}

    /** Streams the remote file straight into storage while hashing it — never touches local disk. */
    private DownloadResult downloadAndStore(String url, UUID tenantId) throws IOException, InterruptedException {
        URI uri = URI.create(url);
        String fileName = Paths.get(uri.getPath()).getFileName().toString();
        String extension = getExtension(fileName);
        if (extension.isBlank()) extension = "pdf";

        HttpRequest request = HttpRequest.newBuilder(uri)
            .GET()
            .timeout(Duration.ofMinutes(5))
            .build();

        HttpResponse<InputStream> response = HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofInputStream());

        if (response.statusCode() >= 300) {
            throw new IOException("Download failed: HTTP " + response.statusCode() + " for " + url);
        }

        String storageKey;
        String fileHash;
        try (DigestInputStream digestStream = FileHashing.wrap(response.body())) {
            storageKey = documentStorageService.store(digestStream, fileName, tenantId.toString());
            fileHash = FileHashing.hexOf(digestStream.getMessageDigest());
        }

        log.info("Downloaded and stored {} → {}", url, storageKey);
        return new DownloadResult(storageKey, fileHash, extension);
    }

    private String getExtension(String filename) {
        int dot = filename.lastIndexOf('.');
        return dot >= 0 ? filename.substring(dot + 1).toLowerCase() : "";
    }

    private String deriveDocName(String url) {
        try {
            String path = URI.create(url).getPath();
            String filename = Paths.get(path).getFileName().toString();
            int dot = filename.lastIndexOf('.');
            return dot >= 0 ? filename.substring(0, dot) : filename;
        } catch (Exception e) {
            return "Untitled";
        }
    }
}

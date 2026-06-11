package com.docai.ingestor.application.service;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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

    @Value("${ingestor.upload-directory:./uploads}")
    private String uploadDirectory;

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
            File downloaded = downloadFile(event.getDownloadUrl(), event.getProduct(), event.getVersion());
            String fileHash = ingestionService.calculateFileHash(downloaded);

            // Skip if already ingested
            if (documentRepository.existsByFileHashAndStatus(fileHash, IngestionStatus.COMPLETED)) {
                log.info("Webhook: document already ingested (hash match). Skipping.");
                downloaded.delete();
                event.setStatus(WebhookEvent.Status.COMPLETED);
                event.setProcessedAt(LocalDateTime.now());
                webhookEventRepository.save(event);
                return;
            }

            Optional<Document> existing = documentRepository.findByFileHash(fileHash);
            Document document;
            if (existing.isPresent()) {
                document = existing.get();
                document.setFilePath(downloaded.getAbsolutePath());
                document.setStatus(IngestionStatus.PROCESSING);
                document.setErrorMessage(null);
                document = documentRepository.save(document);
            } else {
                String docName = event.getDocumentName() != null
                    ? event.getDocumentName()
                    : deriveDocName(event.getDownloadUrl());
                String extension = getExtension(downloaded.getName());

                document = documentRepository.save(Document.builder()
                    .product(event.getProduct())
                    .version(event.getVersion())
                    .documentName(docName)
                    .filePath(downloaded.getAbsolutePath())
                    .fileHash(fileHash)
                    .fileType(extension)
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

    private File downloadFile(String url, String product, String version) throws IOException, InterruptedException {
        URI uri = URI.create(url);
        String fileName = Paths.get(uri.getPath()).getFileName().toString();
        String extension = getExtension(fileName);
        if (extension.isBlank()) extension = "pdf";

        Files.createDirectories(Paths.get(uploadDirectory));
        Path destination = Paths.get(uploadDirectory, UUID.randomUUID() + "." + extension);

        HttpRequest request = HttpRequest.newBuilder(uri)
            .GET()
            .timeout(Duration.ofMinutes(5))
            .build();

        HttpResponse<InputStream> response = HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofInputStream());

        if (response.statusCode() >= 300) {
            throw new IOException("Download failed: HTTP " + response.statusCode() + " for " + url);
        }

        try (InputStream in = response.body()) {
            Files.copy(in, destination);
        }

        log.info("Downloaded {} → {}", url, destination);
        return destination.toFile();
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

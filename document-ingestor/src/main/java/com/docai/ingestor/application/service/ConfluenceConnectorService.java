package com.docai.ingestor.application.service;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.docai.ingestor.domain.entity.ConnectorSyncPage;
import com.docai.ingestor.domain.entity.Document;
import com.docai.ingestor.domain.entity.Document.IngestionStatus;
import com.docai.ingestor.domain.entity.IntegrationToken;
import com.docai.ingestor.domain.repository.ConnectorSyncPageRepository;
import com.docai.ingestor.domain.repository.DocumentRepository;
import com.docai.ingestor.domain.repository.IntegrationTokenRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class ConfluenceConnectorService {

    private final IntegrationTokenRepository tokenRepository;
    private final ConnectorSyncPageRepository syncPageRepository;
    private final DocumentRepository documentRepository;
    private final IngestionService ingestionService;
    private final ObjectMapper objectMapper;

    @Value("${ingestor.upload-directory:./uploads}")
    private String uploadDirectory;

    private static final HttpClient HTTP = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(15))
        .followRedirects(HttpClient.Redirect.NORMAL)
        .build();

    /** Save or update an OAuth token for a user. */
    @Transactional
    public IntegrationToken saveToken(UUID userId, String accessToken, String refreshToken,
                                      Instant expiresAt, String siteUrl) {
        IntegrationToken token = tokenRepository
            .findByUserIdAndProvider(userId, IntegrationToken.Provider.confluence)
            .orElse(IntegrationToken.builder()
                .userId(userId)
                .provider(IntegrationToken.Provider.confluence)
                .build());
        token.setAccessToken(accessToken);
        token.setRefreshToken(refreshToken);
        token.setTokenExpiresAt(expiresAt);
        token.setSiteUrl(siteUrl.replaceAll("/+$", ""));
        return tokenRepository.save(token);
    }

    /** Fetch all pages from a Confluence space and queue them for ingestion. */
    @Async
    @Transactional
    public void syncSpace(UUID tokenId, String spaceKey, String product, String version) {
        IntegrationToken token = tokenRepository.findById(tokenId)
            .orElseThrow(() -> new IllegalArgumentException("Token not found: " + tokenId));

        log.info("Confluence sync started: space={} product={} version={}", spaceKey, product, version);

        try {
            List<JsonNode> pages = fetchAllPages(token, spaceKey);
            log.info("Confluence: found {} pages in space {}", pages.size(), spaceKey);

            for (JsonNode page : pages) {
                syncPage(token, page, spaceKey, product, version);
            }
        } catch (Exception e) {
            log.error("Confluence sync failed for space {}: {}", spaceKey, e.getMessage(), e);
        }
    }

    private List<JsonNode> fetchAllPages(IntegrationToken token, String spaceKey)
            throws IOException, InterruptedException {
        List<JsonNode> all = new ArrayList<>();
        int start = 0;
        final int limit = 50;

        while (true) {
            String url = token.getSiteUrl()
                + "/wiki/rest/api/content?spaceKey=" + spaceKey
                + "&type=page&status=current&expand=version,space"
                + "&start=" + start + "&limit=" + limit;

            JsonNode root = apiGet(token, url);
            JsonNode results = root.path("results");
            for (JsonNode page : results) all.add(page);

            JsonNode nextLink = root.path("_links").path("next");
            if (nextLink.isMissingNode() || nextLink.asText().isBlank()) break;
            start += limit;
        }
        return all;
    }

    private void syncPage(IntegrationToken token, JsonNode page,
                          String spaceKey, String product, String version) {
        String externalId = page.path("id").asText();
        String title = page.path("title").asText("Untitled");
        String remoteModifiedStr = page.path("version").path("when").asText(null);
        Instant remoteModified = remoteModifiedStr != null ? Instant.parse(remoteModifiedStr) : null;

        ConnectorSyncPage record = syncPageRepository
            .findByTokenIdAndExternalId(token.getId(), externalId)
            .orElse(ConnectorSyncPage.builder()
                .tokenId(token.getId())
                .provider("confluence")
                .externalId(externalId)
                .spaceKey(spaceKey)
                .build());

        // Skip if we already synced this version
        if (record.getLastModified() != null && remoteModified != null
                && !remoteModified.isAfter(record.getLastModified())) {
            record.setSyncStatus(ConnectorSyncPage.SyncStatus.SKIPPED);
            syncPageRepository.save(record);
            return;
        }

        record.setTitle(title);
        record.setLastModified(remoteModified);
        record.setSyncStatus(ConnectorSyncPage.SyncStatus.SYNCING);
        syncPageRepository.save(record);

        try {
            // Fetch full HTML body
            String contentUrl = token.getSiteUrl() + "/wiki/rest/api/content/" + externalId
                + "?expand=body.storage";
            JsonNode content = apiGet(token, contentUrl);
            String html = content.path("body").path("storage").path("value").asText("");

            // Save as an HTML file and ingest
            Files.createDirectories(Paths.get(uploadDirectory));
            Path htmlFile = Paths.get(uploadDirectory, "confluence-" + externalId + ".html");
            Files.writeString(htmlFile, html, StandardCharsets.UTF_8);

            Document doc = documentRepository.save(Document.builder()
                .product(product)
                .version(version)
                .documentName("Confluence: " + title)
                .filePath(htmlFile.toAbsolutePath().toString())
                .fileHash(ingestionService.calculateFileHash(htmlFile.toFile()))
                .fileType("html")
                .status(IngestionStatus.PROCESSING)
                .build());

            record.setDocumentId(doc.getId());
            record.setLastSyncedAt(Instant.now());
            record.setSyncStatus(ConnectorSyncPage.SyncStatus.COMPLETED);
            syncPageRepository.save(record);

            ingestionService.ingestUploadedFile(doc.getId());
            log.info("Confluence: synced page '{}' ({})", title, externalId);

        } catch (Exception e) {
            log.error("Confluence: failed to sync page {}: {}", externalId, e.getMessage());
            record.setSyncStatus(ConnectorSyncPage.SyncStatus.FAILED);
            record.setErrorMessage(e.getMessage());
            syncPageRepository.save(record);
        }
    }

    private JsonNode apiGet(IntegrationToken token, String url) throws IOException, InterruptedException {
        HttpRequest req = HttpRequest.newBuilder(URI.create(url))
            .header("Authorization", "Bearer " + token.getAccessToken())
            .header("Accept", "application/json")
            .GET()
            .timeout(Duration.ofSeconds(30))
            .build();

        HttpResponse<String> res = HTTP.send(req, HttpResponse.BodyHandlers.ofString());
        if (res.statusCode() >= 300) {
            throw new IOException("Confluence API error " + res.statusCode() + " for " + url);
        }
        return objectMapper.readTree(res.body());
    }

    public Map<String, Object> getSyncStats(UUID tokenId) {
        long completed = syncPageRepository.countByTokenIdAndSyncStatus(tokenId, ConnectorSyncPage.SyncStatus.COMPLETED);
        long failed = syncPageRepository.countByTokenIdAndSyncStatus(tokenId, ConnectorSyncPage.SyncStatus.FAILED);
        long pending = syncPageRepository.countByTokenIdAndSyncStatus(tokenId, ConnectorSyncPage.SyncStatus.PENDING);
        return Map.of("completed", completed, "failed", failed, "pending", pending);
    }
}

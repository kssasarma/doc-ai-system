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
import com.fasterxml.jackson.databind.node.ObjectNode;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class NotionConnectorService {

    private static final String NOTION_API = "https://api.notion.com/v1";
    private static final String NOTION_VERSION = "2022-06-28";

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

    @Transactional
    public IntegrationToken saveToken(UUID userId, String accessToken,
                                      String workspaceId, String workspaceName) {
        IntegrationToken token = tokenRepository
            .findByUserIdAndProvider(userId, IntegrationToken.Provider.notion)
            .orElse(IntegrationToken.builder()
                .userId(userId)
                .provider(IntegrationToken.Provider.notion)
                .build());
        token.setAccessToken(accessToken);
        token.setWorkspaceId(workspaceId);
        token.setWorkspaceName(workspaceName);
        return tokenRepository.save(token);
    }

    /** Sync all pages accessible to the integration. */
    @Async
    @Transactional
    public void syncAllPages(UUID tokenId, String product, String version) {
        IntegrationToken token = tokenRepository.findById(tokenId)
            .orElseThrow(() -> new IllegalArgumentException("Token not found: " + tokenId));

        log.info("Notion sync started for workspace {} product={} version={}",
            token.getWorkspaceName(), product, version);

        try {
            List<JsonNode> pages = searchAllPages(token);
            log.info("Notion: found {} pages", pages.size());
            for (JsonNode page : pages) {
                syncPage(token, page, product, version);
            }
        } catch (Exception e) {
            log.error("Notion sync failed: {}", e.getMessage(), e);
        }
    }

    private List<JsonNode> searchAllPages(IntegrationToken token) throws IOException, InterruptedException {
        List<JsonNode> all = new ArrayList<>();
        String cursor = null;

        while (true) {
            ObjectNode body = objectMapper.createObjectNode();
            body.set("filter", objectMapper.createObjectNode().put("value", "page").put("property", "object"));
            body.put("page_size", 100);
            if (cursor != null) body.put("start_cursor", cursor);

            JsonNode root = apiPost(token, NOTION_API + "/search", body.toString());
            JsonNode results = root.path("results");
            for (JsonNode page : results) all.add(page);

            if (!root.path("has_more").asBoolean(false)) break;
            cursor = root.path("next_cursor").asText(null);
            if (cursor == null) break;
        }
        return all;
    }

    private void syncPage(IntegrationToken token, JsonNode page, String product, String version) {
        String externalId = page.path("id").asText();
        String remoteModifiedStr = page.path("last_edited_time").asText(null);
        Instant remoteModified = remoteModifiedStr != null ? Instant.parse(remoteModifiedStr) : null;
        String title = extractTitle(page);

        ConnectorSyncPage record = syncPageRepository
            .findByTokenIdAndExternalId(token.getId(), externalId)
            .orElse(ConnectorSyncPage.builder()
                .tokenId(token.getId())
                .provider("notion")
                .externalId(externalId)
                .build());

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
            // Fetch block children and render to plain text
            String text = fetchPageText(token, externalId);
            String content = "# " + title + "\n\n" + text;

            Files.createDirectories(Paths.get(uploadDirectory));
            Path mdFile = Paths.get(uploadDirectory, "notion-" + externalId.replace("-", "") + ".txt");
            Files.writeString(mdFile, content, StandardCharsets.UTF_8);

            Document doc = documentRepository.save(Document.builder()
                .tenantId(Document.DEFAULT_TENANT_ID)
                .product(product)
                .version(version)
                .documentName("Notion: " + title)
                .filePath(mdFile.toAbsolutePath().toString())
                .fileHash(ingestionService.calculateFileHash(mdFile.toFile()))
                .fileType("txt")
                .status(IngestionStatus.PROCESSING)
                .build());

            record.setDocumentId(doc.getId());
            record.setLastSyncedAt(Instant.now());
            record.setSyncStatus(ConnectorSyncPage.SyncStatus.COMPLETED);
            syncPageRepository.save(record);

            ingestionService.ingestUploadedFile(doc.getId());
            log.info("Notion: synced page '{}' ({})", title, externalId);

        } catch (Exception e) {
            log.error("Notion: failed to sync page {}: {}", externalId, e.getMessage());
            record.setSyncStatus(ConnectorSyncPage.SyncStatus.FAILED);
            record.setErrorMessage(e.getMessage());
            syncPageRepository.save(record);
        }
    }

    private String fetchPageText(IntegrationToken token, String pageId)
            throws IOException, InterruptedException {
        JsonNode root = apiGet(token, NOTION_API + "/blocks/" + pageId + "/children?page_size=100");
        StringBuilder sb = new StringBuilder();
        for (JsonNode block : root.path("results")) {
            String type = block.path("type").asText("");
            JsonNode data = block.path(type);
            JsonNode richText = data.path("rich_text");
            if (!richText.isMissingNode()) {
                for (JsonNode rt : richText) {
                    sb.append(rt.path("plain_text").asText(""));
                }
                sb.append("\n");
            }
        }
        return sb.toString();
    }

    private String extractTitle(JsonNode page) {
        try {
            JsonNode props = page.path("properties");
            // Try common title property names
            for (String key : List.of("title", "Title", "Name")) {
                JsonNode prop = props.path(key);
                if (!prop.isMissingNode()) {
                    JsonNode titleArr = prop.path("title");
                    if (titleArr.isArray() && titleArr.size() > 0) {
                        return titleArr.get(0).path("plain_text").asText("Untitled");
                    }
                }
            }
        } catch (Exception ignored) {}
        return "Untitled";
    }

    private JsonNode apiGet(IntegrationToken token, String url) throws IOException, InterruptedException {
        HttpRequest req = HttpRequest.newBuilder(URI.create(url))
            .header("Authorization", "Bearer " + token.getAccessToken())
            .header("Notion-Version", NOTION_VERSION)
            .header("Accept", "application/json")
            .GET()
            .timeout(Duration.ofSeconds(30))
            .build();
        HttpResponse<String> res = HTTP.send(req, HttpResponse.BodyHandlers.ofString());
        if (res.statusCode() >= 300) {
            throw new IOException("Notion API error " + res.statusCode() + " for " + url);
        }
        return objectMapper.readTree(res.body());
    }

    private JsonNode apiPost(IntegrationToken token, String url, String jsonBody)
            throws IOException, InterruptedException {
        HttpRequest req = HttpRequest.newBuilder(URI.create(url))
            .header("Authorization", "Bearer " + token.getAccessToken())
            .header("Notion-Version", NOTION_VERSION)
            .header("Content-Type", "application/json")
            .header("Accept", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
            .timeout(Duration.ofSeconds(30))
            .build();
        HttpResponse<String> res = HTTP.send(req, HttpResponse.BodyHandlers.ofString());
        if (res.statusCode() >= 300) {
            throw new IOException("Notion API error " + res.statusCode() + " for " + url);
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

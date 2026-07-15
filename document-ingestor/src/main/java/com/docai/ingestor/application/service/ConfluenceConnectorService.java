package com.docai.ingestor.application.service;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.docai.ingestor.application.event.DocumentIngestionRequestedEvent;
import com.docai.ingestor.config.SafeUrlValidator;
import com.docai.ingestor.config.TenantContext;
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

    private static final int MAX_REDIRECTS = 3;

    private final IntegrationTokenRepository tokenRepository;
    private final ConnectorSyncPageRepository syncPageRepository;
    private final DocumentRepository documentRepository;
    private final DocumentStorageService documentStorageService;
    private final ApplicationEventPublisher eventPublisher;
    private final ObjectMapper objectMapper;
    private final SafeUrlValidator safeUrlValidator;
    private final DocumentQuotaService documentQuotaService;

    // Redirect.NEVER — see WebhookIngestionService's identical reasoning: SafeUrlValidator must
    // re-validate each redirect hop before it's followed.
    private static final HttpClient HTTP = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(15))
        .followRedirects(HttpClient.Redirect.NEVER)
        .build();

    /** Save or update an OAuth token for a user. Restricts siteUrl to the Confluence host
     * allowlist (default: *.atlassian.net) so a malicious/compromised admin can't point sync at
     * an arbitrary internal host — every subsequent API call carries this stored bearer token. */
    @Transactional
    public IntegrationToken saveToken(UUID userId, String accessToken, String refreshToken,
                                      Instant expiresAt, String siteUrl) {
        String normalizedSiteUrl = siteUrl.replaceAll("/+$", "");
        safeUrlValidator.validateConfluenceSiteUrl(normalizedSiteUrl);

        IntegrationToken token = tokenRepository
            .findByUserIdAndProvider(userId, IntegrationToken.Provider.confluence)
            .orElse(IntegrationToken.builder()
                .userId(userId)
                .tenantId(TenantContext.get())
                .provider(IntegrationToken.Provider.confluence)
                .build());
        token.setAccessToken(accessToken);
        token.setRefreshToken(refreshToken);
        token.setTokenExpiresAt(expiresAt);
        token.setSiteUrl(normalizedSiteUrl);
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
            documentQuotaService.checkQuota(token.getTenantId());

            // Fetch full HTML body
            String contentUrl = token.getSiteUrl() + "/wiki/rest/api/content/" + externalId
                + "?expand=body.storage";
            JsonNode content = apiGet(token, contentUrl);
            String html = content.path("body").path("storage").path("value").asText("");

            byte[] bytes = html.getBytes(StandardCharsets.UTF_8);
            String storageKey = documentStorageService.store(
                new ByteArrayInputStream(bytes), "confluence-" + externalId + ".html",
                token.getTenantId().toString(), bytes.length);

            Document doc = documentRepository.save(Document.builder()
                .tenantId(token.getTenantId())
                .product(product)
                .version(version)
                .documentName("Confluence: " + title)
                .storageKey(storageKey)
                .storageType(documentStorageService.storageType())
                .fileHash(FileHashing.sha256Hex(bytes))
                .fileType("html")
                .status(IngestionStatus.PROCESSING)
                .build());

            record.setDocumentId(doc.getId());
            record.setLastSyncedAt(Instant.now());
            record.setSyncStatus(ConnectorSyncPage.SyncStatus.COMPLETED);
            syncPageRepository.save(record);

            // Published rather than called directly — this method runs inside syncSpace's
            // still-open @Transactional, and ingestUploadedFile is @Async; starting it only after
            // this transaction commits (see IngestionEventListener) avoids it reading the document
            // row before the row actually exists from its point of view.
            eventPublisher.publishEvent(new DocumentIngestionRequestedEvent(doc.getId()));
            log.info("Confluence: synced page '{}' ({})", title, externalId);

        } catch (Exception e) {
            log.error("Confluence: failed to sync page {}: {}", externalId, e.getMessage());
            record.setSyncStatus(ConnectorSyncPage.SyncStatus.FAILED);
            record.setErrorMessage(e.getMessage());
            syncPageRepository.save(record);
        }
    }

    private JsonNode apiGet(IntegrationToken token, String url) throws IOException, InterruptedException {
        String originalHost = URI.create(url).getHost();
        String currentUrl = url;
        for (int hop = 0; ; hop++) {
            safeUrlValidator.validateExternalUrl(currentUrl);

            HttpRequest.Builder reqBuilder = HttpRequest.newBuilder(URI.create(currentUrl))
                .header("Accept", "application/json")
                .GET()
                .timeout(Duration.ofSeconds(30));
            // Only ever attach the bearer token when this hop's host is exactly the host we
            // started with — a redirect to a different host (a CDN, a misconfigured proxy, an
            // attacker-controlled target) must never receive it, even though that host itself
            // passed the general SSRF check (which only rules out private/internal addresses,
            // not "is this the Confluence host the token belongs to").
            if (originalHost.equalsIgnoreCase(URI.create(currentUrl).getHost())) {
                reqBuilder.header("Authorization", "Bearer " + token.getAccessToken());
            }
            HttpRequest req = reqBuilder.build();

            HttpResponse<String> res = HTTP.send(req, HttpResponse.BodyHandlers.ofString());

            if (res.statusCode() >= 300 && res.statusCode() < 400) {
                if (hop >= MAX_REDIRECTS) {
                    throw new IOException("Too many redirects (>" + MAX_REDIRECTS + ") calling Confluence API " + url);
                }
                java.util.Optional<String> location = res.headers().firstValue("Location");
                if (location.isEmpty()) {
                    throw new IOException("Redirect with no Location header for " + currentUrl);
                }
                currentUrl = URI.create(currentUrl).resolve(location.get()).toString();
                continue;
            }
            if (res.statusCode() >= 300) {
                throw new IOException("Confluence API error " + res.statusCode() + " for " + currentUrl);
            }
            return objectMapper.readTree(res.body());
        }
    }

    public Map<String, Object> getSyncStats(UUID tokenId) {
        long completed = syncPageRepository.countByTokenIdAndSyncStatus(tokenId, ConnectorSyncPage.SyncStatus.COMPLETED);
        long failed = syncPageRepository.countByTokenIdAndSyncStatus(tokenId, ConnectorSyncPage.SyncStatus.FAILED);
        long pending = syncPageRepository.countByTokenIdAndSyncStatus(tokenId, ConnectorSyncPage.SyncStatus.PENDING);
        return Map.of("completed", completed, "failed", failed, "pending", pending);
    }
}

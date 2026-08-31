package com.docai.ingestor.application.service;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.UUID;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import com.docai.ingestor.application.service.TenantS3ClientCache.TenantClients;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;

/**
 * Stores documents in an S3-compatible object store. Dispatches each operation to a tenant-specific
 * S3 client when the tenant has configured their own bucket (via the admin storage-config API);
 * falls back to the platform's default bucket otherwise.
 *
 * Storage key format: {@code documents/{tenantId}/{UUID}{ext}} — the tenant ID embedded in the key
 * is used to look up the right S3 client for retrieve/delete/presign operations.
 */
@Slf4j
@Service
@ConditionalOnProperty(name = "ingestor.storage.type", havingValue = "s3", matchIfMissing = true)
@RequiredArgsConstructor
public class S3DocumentStorageService implements DocumentStorageService {

    private final TenantS3ClientCache clientCache;

    @Override
    public String store(InputStream inputStream, String originalName, String tenantId, long contentLength) {
        String ext = originalName.contains(".")
            ? originalName.substring(originalName.lastIndexOf('.'))
            : "";
        String key = "documents/" + tenantId + "/" + UUID.randomUUID() + ext;
        TenantClients clients = clientCache.getClients(UUID.fromString(tenantId));
        try {
            // Known length: stream straight through to S3 — no full-file buffer in this process's
            // heap, closing the "100MB upload × N concurrent requests" OOM vector. Unknown length
            // (a network download with no reliable Content-Length) falls back to buffering, but
            // every such caller already caps the stream's size itself (see LimitedInputStream),
            // so this is a bounded, not unbounded, buffer.
            RequestBody body = contentLength >= 0
                ? RequestBody.fromInputStream(inputStream, contentLength)
                : RequestBody.fromBytes(inputStream.readAllBytes());
            clients.client().putObject(
                PutObjectRequest.builder().bucket(clients.bucket()).key(key).build(), body);
            log.debug("Stored file in S3: s3://{}/{}", clients.bucket(), key);
            return key;
        } catch (IOException e) {
            throw new RuntimeException("Failed to read upload stream for S3", e);
        }
    }

    @Override
    public Path resolve(String storageKey) {
        TenantClients clients = clientsFor(storageKey);
        try {
            // createTempFile only to reserve a unique, collision-free path — it must not exist
            // when the SDK writes to it below, so delete the (empty) placeholder immediately.
            Path tmp = Files.createTempFile("docai-s3-", storageKey.replaceAll("[/:]", "_"));
            Files.delete(tmp);
            clients.client().getObject(
                GetObjectRequest.builder().bucket(clients.bucket()).key(storageKey).build(),
                tmp);
            tmp.toFile().deleteOnExit();
            return tmp;
        } catch (IOException e) {
            throw new RuntimeException("Failed to download S3 object: " + storageKey, e);
        }
    }

    @Override
    public boolean exists(String storageKey) {
        TenantClients clients = clientsFor(storageKey);
        try {
            clients.client().headObject(
                HeadObjectRequest.builder().bucket(clients.bucket()).key(storageKey).build());
            return true;
        } catch (NoSuchKeyException e) {
            return false;
        }
    }

    @Override
    public void delete(String storageKey) {
        TenantClients clients = clientsFor(storageKey);
        try {
            clients.client().deleteObject(
                DeleteObjectRequest.builder().bucket(clients.bucket()).key(storageKey).build());
        } catch (Exception e) {
            log.warn("Could not delete S3 object {}: {}", storageKey, e.getMessage());
        }
    }

    @Override
    public String storageType() {
        return "S3";
    }

    @Override
    public String presignedDownloadUrl(String storageKey, Duration ttl) {
        if (!exists(storageKey)) {
            throw new IllegalStateException("File not found in storage: " + storageKey);
        }
        TenantClients clients = clientsFor(storageKey);
        GetObjectPresignRequest presignRequest = GetObjectPresignRequest.builder()
            .signatureDuration(ttl)
            .getObjectRequest(
                GetObjectRequest.builder().bucket(clients.bucket()).key(storageKey).build())
            .build();
        return clients.presigner().presignGetObject(presignRequest).url().toString();
    }

    /**
     * Extracts the tenant ID from a storage key of the form {@code documents/{tenantId}/...}
     * and returns the appropriate S3 clients for that tenant.
     */
    private TenantClients clientsFor(String storageKey) {
        try {
            String[] parts = storageKey.split("/");
            UUID tenantId = UUID.fromString(parts[1]);
            return clientCache.getClients(tenantId);
        } catch (Exception e) {
            log.warn("Could not extract tenant ID from storage key '{}' — using platform client", storageKey);
            return clientCache.getClients(null);
        }
    }
}

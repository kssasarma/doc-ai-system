package com.docai.ingestor.application.service;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

/**
 * Stores documents in AWS S3 (or MinIO via compatible endpoint).
 * Active when ingestor.storage.type=s3.
 */
@Slf4j
@Service
@ConditionalOnProperty(name = "ingestor.storage.type", havingValue = "s3")
@RequiredArgsConstructor
public class S3DocumentStorageService implements DocumentStorageService {

    private final S3Client s3Client;

    @Value("${ingestor.storage.s3.bucket}")
    private String bucket;

    @Override
    public String store(InputStream inputStream, String originalName, String tenantId) {
        String ext = originalName.contains(".")
            ? originalName.substring(originalName.lastIndexOf('.'))
            : "";
        String key = "documents/" + tenantId + "/" + UUID.randomUUID() + ext;
        try {
            byte[] bytes = inputStream.readAllBytes();
            s3Client.putObject(
                PutObjectRequest.builder().bucket(bucket).key(key).build(),
                RequestBody.fromBytes(bytes));
            log.debug("Stored file in S3: s3://{}/{}", bucket, key);
            return key;
        } catch (IOException e) {
            throw new RuntimeException("Failed to read upload stream for S3", e);
        }
    }

    @Override
    public Path resolve(String storageKey) {
        try {
            Path tmp = Files.createTempFile("docai-s3-", storageKey.replaceAll("[/:]", "_"));
            s3Client.getObject(
                GetObjectRequest.builder().bucket(bucket).key(storageKey).build(),
                tmp);
            tmp.toFile().deleteOnExit();
            return tmp;
        } catch (IOException e) {
            throw new RuntimeException("Failed to download S3 object: " + storageKey, e);
        }
    }

    @Override
    public void delete(String storageKey) {
        try {
            s3Client.deleteObject(
                DeleteObjectRequest.builder().bucket(bucket).key(storageKey).build());
        } catch (Exception e) {
            log.warn("Could not delete S3 object {}: {}", storageKey, e.getMessage());
        }
    }

    @Override
    public String storageType() {
        return "S3";
    }
}

package com.docai.ingestor.application.service;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import lombok.extern.slf4j.Slf4j;

/**
 * Stores documents on the local filesystem.
 * Active when ingestor.storage.type=local (the default).
 */
@Slf4j
@Service
@ConditionalOnProperty(name = "ingestor.storage.type", havingValue = "local", matchIfMissing = true)
public class LocalDocumentStorageService implements DocumentStorageService {

    private final Path uploadRoot;

    public LocalDocumentStorageService(
            @Value("${ingestor.upload-directory:./uploads}") String uploadDir) {
        this.uploadRoot = Paths.get(uploadDir);
        try {
            Files.createDirectories(uploadRoot);
        } catch (IOException e) {
            throw new IllegalStateException("Cannot create upload directory: " + uploadDir, e);
        }
    }

    @Override
    public String store(InputStream inputStream, String originalName, String tenantId) {
        String ext = originalName.contains(".")
            ? originalName.substring(originalName.lastIndexOf('.'))
            : "";
        String key = tenantId + "/" + UUID.randomUUID() + ext;
        Path target = uploadRoot.resolve(key);
        try {
            Files.createDirectories(target.getParent());
            Files.copy(inputStream, target, StandardCopyOption.REPLACE_EXISTING);
            log.debug("Stored file locally: {}", key);
            return key;
        } catch (IOException e) {
            throw new RuntimeException("Failed to store file: " + originalName, e);
        }
    }

    @Override
    public Path resolve(String storageKey) {
        return uploadRoot.resolve(storageKey);
    }

    @Override
    public void delete(String storageKey) {
        try {
            Files.deleteIfExists(uploadRoot.resolve(storageKey));
        } catch (IOException e) {
            log.warn("Could not delete local file {}: {}", storageKey, e.getMessage());
        }
    }

    @Override
    public String storageType() {
        return "LOCAL";
    }
}

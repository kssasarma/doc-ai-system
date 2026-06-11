package com.docai.ingestor.application.service;

import java.io.InputStream;
import java.nio.file.Path;

/**
 * Storage abstraction for ingested document files.
 * Implementations: LocalDocumentStorageService (default), S3DocumentStorageService.
 */
public interface DocumentStorageService {

    /**
     * Persist an uploaded file and return its storage key (path or S3 object key).
     *
     * @param inputStream  raw bytes of the uploaded file
     * @param originalName original filename (used to derive extension / key)
     * @param tenantId     tenant to scope the storage path
     * @return storage key — passed to {@link #resolve(String)} to read the file back
     */
    String store(InputStream inputStream, String originalName, String tenantId);

    /**
     * Return a local {@link Path} suitable for passing to Apache Tika.
     * For S3, the file is downloaded to a temp location first.
     */
    Path resolve(String storageKey);

    /** Delete the file at the given storage key. No-op if not found. */
    void delete(String storageKey);

    /** Storage type label written to the documents.storage_type column. */
    String storageType();
}

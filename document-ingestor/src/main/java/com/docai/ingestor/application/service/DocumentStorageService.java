package com.docai.ingestor.application.service;

import java.io.InputStream;
import java.nio.file.Path;
import java.time.Duration;

/**
 * Storage abstraction for ingested document files — the only thing any ingestion code path
 * (upload, webhook download, Confluence/Notion sync) knows about where files live. Adding a new
 * backend (GCS, Azure Blob, ...) means adding one new implementation gated by
 * {@code ingestor.storage.type}; nothing else in the codebase changes.
 */
public interface DocumentStorageService {

    /**
     * Persist a file and return its storage key.
     *
     * @param inputStream   raw bytes of the file
     * @param originalName  original filename (used to derive extension / key)
     * @param tenantId      tenant to scope the storage path
     * @param contentLength exact byte length of {@code inputStream} if known upfront (e.g. a
     *                      multipart upload's declared size, or an already-in-memory byte array)
     *                      — lets the implementation stream directly to storage without buffering
     *                      the whole file. Pass {@code -1} when unknown (e.g. a network download
     *                      with no reliable Content-Length); the implementation then falls back to
     *                      buffering, bounded by whatever size cap the caller already enforces.
     * @return storage key — passed to {@link #resolve(String)}/{@link #exists(String)}/{@link #delete(String)}
     */
    String store(InputStream inputStream, String originalName, String tenantId, long contentLength);

    /**
     * Return a local {@link Path} suitable for passing to Apache Tika. The caller owns the
     * returned file's lifecycle and must delete it once done — this is always a fresh working
     * copy, never the authoritative stored file.
     */
    Path resolve(String storageKey);

    /** Whether a file exists at the given storage key. */
    boolean exists(String storageKey);

    /** Delete the file at the given storage key. No-op if not found. */
    void delete(String storageKey);

    /** Storage type label written to the documents.storage_type column. */
    String storageType();

    /**
     * A time-limited URL the caller can download the file from directly, without proxying bytes
     * through this service. Only meaningful for documents that still have a live storage object —
     * completed documents' source files are deleted post-ingestion (see IngestionService), so
     * this throws {@link IllegalStateException} rather than a broken/expired-looking URL.
     */
    String presignedDownloadUrl(String storageKey, Duration ttl);
}

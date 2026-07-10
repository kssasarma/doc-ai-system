package com.docai.ingestor.application.service;

import java.io.InputStream;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * SHA-256 hashing helpers shared by every ingestion entry point (upload, webhook download,
 * Confluence/Notion sync). Content is hashed for dedup purposes; this exists so no caller needs
 * to buffer a file to local disk just to compute a hash before storing it — {@link #wrap} lets
 * the hash be computed in the same pass as streaming the content into storage.
 */
public final class FileHashing {

    private FileHashing() {}

    /** Wraps a stream so its SHA-256 digest is computed as the stream is consumed elsewhere. */
    public static DigestInputStream wrap(InputStream in) {
        return new DigestInputStream(in, newSha256());
    }

    /** Call after fully consuming a stream returned by {@link #wrap}. */
    public static String hexOf(MessageDigest digest) {
        return HexFormat.of().formatHex(digest.digest());
    }

    /** For content already fully materialized in memory (e.g. Confluence/Notion page bodies). */
    public static String sha256Hex(byte[] bytes) {
        return HexFormat.of().formatHex(newSha256().digest(bytes));
    }

    private static MessageDigest newSha256() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            // SHA-256 is a required algorithm on every JVM implementation — cannot happen.
            throw new IllegalStateException(e);
        }
    }
}

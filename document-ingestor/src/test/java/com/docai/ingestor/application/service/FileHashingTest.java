package com.docai.ingestor.application.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.DigestInputStream;

import org.junit.jupiter.api.Test;

class FileHashingTest {

    @Test
    void sha256Hex_sameContent_returnsSameHash() {
        byte[] a = "deterministic content".getBytes(StandardCharsets.UTF_8);
        byte[] b = "deterministic content".getBytes(StandardCharsets.UTF_8);

        String h1 = FileHashing.sha256Hex(a);
        String h2 = FileHashing.sha256Hex(b);

        assertThat(h1).isEqualTo(h2);
        assertThat(h1).hasSize(64); // SHA-256 hex
    }

    @Test
    void sha256Hex_differentContent_returnsDifferentHashes() {
        String h1 = FileHashing.sha256Hex("content A".getBytes(StandardCharsets.UTF_8));
        String h2 = FileHashing.sha256Hex("content B".getBytes(StandardCharsets.UTF_8));

        assertThat(h1).isNotEqualTo(h2);
    }

    @Test
    void wrap_computesSameHashAsSha256Hex_whileStreamIsConsumedElsewhere() throws Exception {
        byte[] content = "streamed content to hash while it's being read".getBytes(StandardCharsets.UTF_8);
        String expected = FileHashing.sha256Hex(content);

        try (InputStream source = new ByteArrayInputStream(content);
             DigestInputStream digestStream = FileHashing.wrap(source)) {
            // Simulate a caller (e.g. an S3 client) fully consuming the wrapped stream.
            digestStream.readAllBytes();
            assertThat(FileHashing.hexOf(digestStream.getMessageDigest())).isEqualTo(expected);
        }
    }
}

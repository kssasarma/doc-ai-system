package com.docai.bot.application.service;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import lombok.extern.slf4j.Slf4j;

/**
 * AES-256-GCM encryption for secrets stored at rest (per-tenant LLM API keys, integration OAuth
 * tokens). Key comes from {@code SECRETS_ENCRYPTION_KEY} (base64, must decode to exactly 32
 * bytes) — required in the {@code prod} profile via application-prod.yml's placeholder-with-no-
 * default convention (same pattern as JWT_SECRET/SEED_ADMIN_PASSWORD), optional in dev where an
 * ephemeral key is generated (encrypted values won't survive a restart — acceptable for local
 * dev, never for prod).
 */
@Slf4j
@Service
public class SecretsCryptoService {

    private static final String TRANSFORMATION = "AES/GCM/NoPadding";
    private static final int IV_LENGTH_BYTES = 12;
    private static final int TAG_LENGTH_BITS = 128;
    private static final int KEY_LENGTH_BYTES = 32;

    private final SecretKeySpec key;

    public SecretsCryptoService(@Value("${app.secrets.encryption-key:}") String base64Key) {
        if (base64Key == null || base64Key.isBlank()) {
            log.warn("SECRETS_ENCRYPTION_KEY is not set — generating an ephemeral in-memory key. "
                + "Any secret encrypted this run (tenant LLM API keys, integration tokens) becomes "
                + "UNDECRYPTABLE after a restart. This is acceptable for local development only; "
                + "the prod profile requires this variable and refuses to boot without it.");
            byte[] random = new byte[KEY_LENGTH_BYTES];
            new SecureRandom().nextBytes(random);
            this.key = new SecretKeySpec(random, "AES");
            return;
        }
        byte[] decoded = Base64.getDecoder().decode(base64Key);
        if (decoded.length != KEY_LENGTH_BYTES) {
            throw new IllegalStateException(
                "SECRETS_ENCRYPTION_KEY must decode to exactly 32 bytes (AES-256), got " + decoded.length
                + ". Generate one with: openssl rand -base64 32");
        }
        this.key = new SecretKeySpec(decoded, "AES");
    }

    /** Encrypts plaintext; returns a base64 blob (random 12-byte IV || ciphertext+tag). Null-safe. */
    public String encrypt(String plaintext) {
        if (plaintext == null) return null;
        try {
            byte[] iv = new byte[IV_LENGTH_BYTES];
            new SecureRandom().nextBytes(iv);
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG_LENGTH_BITS, iv));
            byte[] ciphertext = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
            ByteBuffer buffer = ByteBuffer.allocate(iv.length + ciphertext.length);
            buffer.put(iv).put(ciphertext);
            return Base64.getEncoder().encodeToString(buffer.array());
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Failed to encrypt secret", e);
        }
    }

    /** Decrypts a blob produced by {@link #encrypt}. Returns null (not a throw) on any failure —
     * a wrong/rotated key must degrade to "no custom key configured", not a 500. */
    public String decrypt(String encoded) {
        if (encoded == null || encoded.isBlank()) return null;
        try {
            byte[] all = Base64.getDecoder().decode(encoded);
            byte[] iv = Arrays.copyOfRange(all, 0, IV_LENGTH_BYTES);
            byte[] ciphertext = Arrays.copyOfRange(all, IV_LENGTH_BYTES, all.length);
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_LENGTH_BITS, iv));
            return new String(cipher.doFinal(ciphertext), StandardCharsets.UTF_8);
        } catch (Exception e) {
            log.error("Failed to decrypt a stored secret (wrong/rotated SECRETS_ENCRYPTION_KEY, or "
                + "corrupted data) — treating as absent: {}", e.getMessage());
            return null;
        }
    }
}

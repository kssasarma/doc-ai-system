package com.docai.ingestor.config;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import com.docai.ingestor.application.service.SecretsCryptoService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * One-time, idempotent startup pass that encrypts any {@code integration_tokens} rows still
 * holding plaintext access/refresh tokens from before {@code EncryptedStringConverter} existed.
 * Uses raw JDBC (not the JPA entity) so it never goes through the converter itself.
 *
 * <p>Idempotency comes from AES-GCM's authentication tag: {@link SecretsCryptoService#decrypt}
 * only returns non-null for a blob it actually produced with the current key — for anything else
 * (plaintext, or ciphertext under a different key) it returns null. So a row that already decrypts
 * successfully is left alone; only rows that don't are treated as plaintext and encrypted in place.
 * Safe to run on every boot — already-migrated rows are a no-op single SELECT.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class IntegrationTokenEncryptionMigration implements ApplicationRunner {

    private final JdbcTemplate jdbcTemplate;
    private final SecretsCryptoService cryptoService;

    @Override
    public void run(ApplicationArguments args) {
        List<Map<String, Object>> rows;
        try {
            rows = jdbcTemplate.queryForList(
                "SELECT id, access_token, refresh_token FROM integration_tokens");
        } catch (Exception e) {
            log.warn("Skipping integration token encryption migration: {}", e.getMessage());
            return;
        }

        int migrated = 0;
        for (Map<String, Object> row : rows) {
            UUID id = (UUID) row.get("id");
            String accessToken = (String) row.get("access_token");
            String refreshToken = (String) row.get("refresh_token");

            boolean accessNeedsMigration = accessToken != null && cryptoService.decrypt(accessToken) == null;
            boolean refreshNeedsMigration = refreshToken != null && cryptoService.decrypt(refreshToken) == null;
            if (!accessNeedsMigration && !refreshNeedsMigration) continue;

            String newAccess = accessNeedsMigration ? cryptoService.encrypt(accessToken) : accessToken;
            String newRefresh = refreshNeedsMigration ? cryptoService.encrypt(refreshToken) : refreshToken;
            jdbcTemplate.update(
                "UPDATE integration_tokens SET access_token = ?, refresh_token = ? WHERE id = ?",
                newAccess, newRefresh, id);
            migrated++;
        }

        if (migrated > 0) {
            log.info("Encrypted {} plaintext integration_tokens row(s) at rest", migrated);
        }
    }
}

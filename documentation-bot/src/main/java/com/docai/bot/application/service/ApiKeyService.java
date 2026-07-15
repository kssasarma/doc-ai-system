package com.docai.bot.application.service;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.docai.bot.domain.entity.ApiKey;
import com.docai.bot.domain.repository.ApiKeyRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class ApiKeyService {

    private static final String PREFIX = "sk-docai-";
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final ApiKeyRepository apiKeyRepository;
    private final PasswordEncoder passwordEncoder;

    @Transactional
    public CreateKeyResult createKey(UUID userId, String name, String[] scopes, Integer rateLimitPerMin,
                                     Integer expirationDays) {
        byte[] randomBytes = new byte[24];
        SECURE_RANDOM.nextBytes(randomBytes);
        String rawToken = PREFIX + Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes);
        String keyHash = passwordEncoder.encode(rawToken);
        String keyPrefix = rawToken.substring(0, Math.min(12, rawToken.length()));

        String[] effectiveScopes = (scopes != null && scopes.length > 0) ? scopes : new String[]{"query"};
        int rateLimit = (rateLimitPerMin != null && rateLimitPerMin > 0) ? rateLimitPerMin : 60;
        LocalDateTime expiresAt = expirationDays != null && expirationDays > 0
            ? LocalDateTime.now().plusDays(expirationDays) : null;

        ApiKey apiKey = ApiKey.builder()
            .userId(userId)
            .keyHash(keyHash)
            .keyPrefix(keyPrefix)
            .name(name)
            .scopes(effectiveScopes)
            .rateLimitPerMin(rateLimit)
            .expiresAt(expiresAt)
            .revoked(false)
            .build();

        ApiKey saved = apiKeyRepository.save(apiKey);
        log.info("Created API key '{}' for user {}", name, userId);

        return new CreateKeyResult(toDTO(saved), rawToken);
    }

    @Transactional(readOnly = true)
    public List<ApiKeyDTO> listKeys(UUID userId) {
        return apiKeyRepository.findByUserIdOrderByCreatedAtDesc(userId).stream()
            .map(this::toDTO)
            .collect(Collectors.toList());
    }

    @Transactional
    public void revokeKey(UUID keyId, UUID userId) {
        ApiKey key = apiKeyRepository.findById(keyId)
            .orElseThrow(() -> new IllegalArgumentException("API key not found"));
        if (!key.getUserId().equals(userId)) {
            throw new IllegalArgumentException("Not authorized to revoke this key");
        }
        key.setRevoked(true);
        apiKeyRepository.save(key);
        log.info("Revoked API key {} for user {}", keyId, userId);
    }

    @Transactional
    public void revokeKeyAsAdmin(UUID keyId) {
        ApiKey key = apiKeyRepository.findById(keyId)
            .orElseThrow(() -> new IllegalArgumentException("API key not found"));
        key.setRevoked(true);
        apiKeyRepository.save(key);
        log.info("Admin revoked API key {}", keyId);
    }

    @Transactional(readOnly = true)
    public Optional<ApiKey> validateKey(String rawToken) {
        if (rawToken == null || rawToken.length() < 12) return Optional.empty();
        // Narrow to the (indexed) key_prefix match first — at most a handful of candidates even
        // with many keys in the table — then BCrypt-compare only those, instead of every row.
        String prefix = rawToken.substring(0, 12);
        return apiKeyRepository.findByKeyPrefix(prefix).stream()
            .filter(k -> k.isActive() && passwordEncoder.matches(rawToken, k.getKeyHash()))
            .findFirst();
    }

    @Transactional
    public void touchLastUsed(UUID keyId) {
        apiKeyRepository.updateLastUsed(keyId);
    }

    private ApiKeyDTO toDTO(ApiKey k) {
        return ApiKeyDTO.builder()
            .id(k.getId().toString())
            .userId(k.getUserId().toString())
            .keyPrefix(k.getKeyPrefix())
            .name(k.getName())
            .scopes(Arrays.asList(k.getScopes() != null ? k.getScopes() : new String[0]))
            .rateLimitPerMin(k.getRateLimitPerMin())
            .revoked(k.isRevoked())
            .lastUsedAt(k.getLastUsedAt() != null ? k.getLastUsedAt().toString() : null)
            .expiresAt(k.getExpiresAt() != null ? k.getExpiresAt().toString() : null)
            .createdAt(k.getCreatedAt() != null ? k.getCreatedAt().toString() : null)
            .build();
    }

    // ── DTOs ──────────────────────────────────────────────────────────────────

    public record CreateKeyResult(ApiKeyDTO dto, String rawKey) {}

    @lombok.Data @lombok.Builder
    public static class ApiKeyDTO {
        private String id;
        private String userId;
        private String keyPrefix;
        private String name;
        private List<String> scopes;
        private int rateLimitPerMin;
        private boolean revoked;
        private String lastUsedAt;
        private String expiresAt;
        private String createdAt;
    }
}

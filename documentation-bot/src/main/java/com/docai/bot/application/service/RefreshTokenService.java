package com.docai.bot.application.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.HexFormat;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.docai.bot.domain.entity.RefreshToken;
import com.docai.bot.domain.entity.User;
import com.docai.bot.domain.repository.RefreshTokenRepository;
import com.docai.bot.domain.repository.UserRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Refresh tokens let the frontend silently renew a session instead of forcing a full re-login
 * once the (deliberately short-lived) access JWT expires. Tokens are single-use: each renewal
 * rotates to a new raw token and revokes the old one, so a token presented a second time (theft
 * or a replayed request) is detected as reuse rather than silently accepted.
 *
 * <p>Only the SHA-256 hash of the raw token is persisted — same principle as {@link ApiKeyService}
 * — but unlike API keys/passwords this uses a fast deterministic hash rather than BCrypt: refresh
 * tokens are high-entropy random values (not human-guessable), so there's no dictionary-attack
 * risk to defend against, and every renewal needs an indexed exact-match lookup rather than the
 * O(n) BCrypt scan API-key validation uses.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RefreshTokenService {

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final RefreshTokenRepository refreshTokenRepository;
    private final UserRepository userRepository;

    @Value("${app.jwt.refresh-expiration-ms:2592000000}") // 30 days
    private long refreshExpirationMs;

    @Transactional
    public String issue(UUID userId) {
        byte[] randomBytes = new byte[32];
        SECURE_RANDOM.nextBytes(randomBytes);
        String rawToken = Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes);

        RefreshToken entity = RefreshToken.builder()
            .userId(userId)
            .tokenHash(hash(rawToken))
            .expiresAt(LocalDateTime.now().plus(Duration.ofMillis(refreshExpirationMs)))
            .build();
        refreshTokenRepository.save(entity);
        return rawToken;
    }

    /** Validates and rotates a refresh token, returning the (freshly loaded, so it reflects any
     * tenant switch since the token was issued) user and a new raw refresh token. Throws if the
     * token is missing, expired, revoked, already rotated, or its owner has been erased. */
    @Transactional
    public RotationResult rotate(String rawToken) {
        RefreshToken existing = refreshTokenRepository.findByTokenHash(hash(rawToken))
            .orElseThrow(() -> new IllegalArgumentException("Invalid refresh token"));

        if (existing.getReplacedBy() != null) {
            log.warn("Refresh token reuse detected for user {} — revoking all their sessions", existing.getUserId());
            revokeAllForUser(existing.getUserId());
            throw new IllegalArgumentException("Refresh token has already been used");
        }
        if (!existing.isActive()) {
            throw new IllegalArgumentException("Refresh token is expired or revoked");
        }

        User user = userRepository.findById(existing.getUserId())
            .filter(u -> u.getDeletedAt() == null)
            .orElseThrow(() -> new IllegalArgumentException("Invalid refresh token"));

        String newRawToken = issue(user.getId());
        RefreshToken newEntity = refreshTokenRepository.findByTokenHash(hash(newRawToken))
            .orElseThrow(() -> new IllegalStateException("Just-issued refresh token vanished"));

        existing.setRevokedAt(LocalDateTime.now());
        existing.setReplacedBy(newEntity.getId());
        refreshTokenRepository.save(existing);

        return new RotationResult(user, newRawToken);
    }

    @Transactional
    public void revoke(String rawToken) {
        refreshTokenRepository.findByTokenHash(hash(rawToken)).ifPresent(t -> {
            t.setRevokedAt(LocalDateTime.now());
            refreshTokenRepository.save(t);
        });
    }

    @Transactional
    public void revokeAllForUser(UUID userId) {
        refreshTokenRepository.deleteByUserId(userId);
    }

    private String hash(String rawToken) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(rawToken.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }

    public record RotationResult(User user, String rawToken) {}
}

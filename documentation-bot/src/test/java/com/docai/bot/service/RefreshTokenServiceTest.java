package com.docai.bot.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import com.docai.bot.PostgresTestContainerBase;
import com.docai.bot.application.service.RefreshTokenService;
import com.docai.bot.application.service.RefreshTokenService.RotationResult;
import com.docai.bot.domain.entity.Tenant;
import com.docai.bot.domain.entity.User;
import com.docai.bot.domain.repository.RefreshTokenRepository;
import com.docai.bot.domain.repository.TenantRepository;
import com.docai.bot.domain.repository.UserRepository;

@Transactional
class RefreshTokenServiceTest extends PostgresTestContainerBase {

    @Autowired RefreshTokenService refreshTokenService;
    @Autowired RefreshTokenRepository refreshTokenRepository;
    @Autowired TenantRepository tenantRepository;
    @Autowired UserRepository userRepository;

    @Test
    void rotate_withValidToken_issuesNewTokenAndRevokesOld() {
        User user = persistUser();
        String raw = refreshTokenService.issue(user.getId());

        RotationResult result = refreshTokenService.rotate(raw);

        assertThat(result.user().getId()).isEqualTo(user.getId());
        assertThat(result.rawToken()).isNotEqualTo(raw);
        assertThat(refreshTokenRepository.findByTokenHash(sha256(raw)).orElseThrow().getRevokedAt()).isNotNull();
    }

    @Test
    void rotate_reflectsCurrentTenantOnUserRow_notTheOneAtIssueTime() {
        Tenant original = persistTenant("original-tenant");
        Tenant switched = persistTenant("switched-tenant");
        User user = persistUser(original);
        String raw = refreshTokenService.issue(user.getId());

        // Simulate a tenant switch that happened after the refresh token was issued.
        user.setTenantId(switched.getId());
        userRepository.save(user);

        RotationResult result = refreshTokenService.rotate(raw);

        assertThat(result.user().getTenantId()).isEqualTo(switched.getId());
    }

    @Test
    void rotate_withAlreadyRotatedToken_throwsAndRevokesAllSessionsForThatUser() {
        User user = persistUser();
        String raw = refreshTokenService.issue(user.getId());
        String otherRaw = refreshTokenService.issue(user.getId());
        refreshTokenService.rotate(raw); // first use — legitimate

        assertThatThrownBy(() -> refreshTokenService.rotate(raw)) // replay — reuse detected
            .isInstanceOf(IllegalArgumentException.class);

        // The reuse response revokes every session for this user, including the unrelated one.
        assertThatThrownBy(() -> refreshTokenService.rotate(otherRaw))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rotate_withRevokedToken_throws() {
        User user = persistUser();
        String raw = refreshTokenService.issue(user.getId());
        refreshTokenService.revoke(raw);

        assertThatThrownBy(() -> refreshTokenService.rotate(raw))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rotate_withUnknownToken_throws() {
        assertThatThrownBy(() -> refreshTokenService.rotate("not-a-real-token"))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void revokeAllForUser_invalidatesEveryOutstandingToken() {
        User user = persistUser();
        String a = refreshTokenService.issue(user.getId());
        String b = refreshTokenService.issue(user.getId());

        refreshTokenService.revokeAllForUser(user.getId());

        assertThatThrownBy(() -> refreshTokenService.rotate(a)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> refreshTokenService.rotate(b)).isInstanceOf(IllegalArgumentException.class);
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private Tenant persistTenant(String slug) {
        return tenantRepository.save(Tenant.builder().name(slug).slug(slug).build());
    }

    private User persistUser() {
        return persistUser(persistTenant("tenant-" + UUID.randomUUID().toString().substring(0, 8)));
    }

    private User persistUser(Tenant tenant) {
        String unique = UUID.randomUUID().toString().substring(0, 8);
        return userRepository.save(User.builder()
            .username("user-" + unique)
            .email("user-" + unique + "@example.com")
            .passwordHash("irrelevant-for-this-test")
            .role(User.Role.USER)
            .tenantId(tenant.getId())
            .build());
    }

    private String sha256(String rawToken) {
        try {
            java.security.MessageDigest digest = java.security.MessageDigest.getInstance("SHA-256");
            return java.util.HexFormat.of().formatHex(digest.digest(rawToken.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}

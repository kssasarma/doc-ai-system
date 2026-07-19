package com.docai.bot.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.env.Environment;
import org.springframework.test.util.ReflectionTestUtils;

import com.docai.bot.application.service.JwtService;
import com.docai.bot.domain.entity.User;

@ExtendWith(MockitoExtension.class)
class JwtServiceTest {

    @Mock
    private Environment environment;

    private JwtService jwtService;

    // 64-byte base64 secret — large enough for HS256, not the dev default
    private static final String TEST_SECRET = "dGVzdC1vbmx5LWp3dC1zZWNyZXQtbm90LXVzZWQtZm9yLXJlYWwtdG9rZW5zLTEyMzQ1Njc4OTAxMjM0NTY=";

    @BeforeEach
    void setUp() {
        // @PostConstruct (validateSecretIsNotDevDefault) doesn't run on manual instantiation,
        // so environment.getActiveProfiles() is never called — no stubbing needed.
        jwtService = new JwtService(environment);
        ReflectionTestUtils.setField(jwtService, "secret", TEST_SECRET);
        ReflectionTestUtils.setField(jwtService, "expirationMs", 86_400_000L);
    }

    @Test
    void generateToken_adminUser_returnsValidJwt() {
        User user = adminUser();
        String token = jwtService.generateToken(user);
        assertThat(token).isNotBlank();
        assertThat(token.split("\\.")).hasSize(3);
    }

    @Test
    void extractUserId_fromGeneratedToken_matchesOriginal() {
        User user = adminUser();
        String token = jwtService.generateToken(user);
        UUID extracted = jwtService.extractUserId(token);
        assertThat(extracted).isEqualTo(user.getId());
    }

    @Test
    void extractRole_fromGeneratedToken_matchesOriginal() {
        User user = adminUser();
        String token = jwtService.generateToken(user);
        assertThat(jwtService.extractRole(token)).isEqualTo("ADMIN");
    }

    @Test
    void extractUsername_fromGeneratedToken_matchesOriginal() {
        User user = adminUser();
        String token = jwtService.generateToken(user);
        assertThat(jwtService.extractUsername(token)).isEqualTo("alice");
    }

    @Test
    void extractTenantId_adminUser_matchesOriginal() {
        User user = adminUser();
        String token = jwtService.generateToken(user);
        assertThat(jwtService.extractTenantId(token)).isEqualTo(user.getTenantId());
    }

    @Test
    void extractTenantId_superAdmin_returnsNull() {
        User superAdmin = User.builder()
            .id(UUID.randomUUID())
            .username("superadmin")
            .email("super@example.com")
            .passwordHash("hash")
            .role(User.Role.SUPER_ADMIN)
            .tenantId(null)
            .build();

        String token = jwtService.generateToken(superAdmin);
        assertThat(jwtService.extractTenantId(token)).isNull();
    }

    @Test
    void extractMustChangePassword_whenTrue_returnsTrue() {
        User user = User.builder()
            .id(UUID.randomUUID())
            .username("newuser")
            .email("new@example.com")
            .passwordHash("hash")
            .role(User.Role.USER)
            .tenantId(UUID.randomUUID())
            .mustChangePassword(true)
            .build();

        String token = jwtService.generateToken(user);
        assertThat(jwtService.extractMustChangePassword(token)).isTrue();
    }

    @Test
    void isTokenValid_validToken_returnsTrue() {
        String token = jwtService.generateToken(adminUser());
        assertThat(jwtService.isTokenValid(token)).isTrue();
    }

    @Test
    void isTokenValid_tamperedToken_returnsFalse() {
        String token = jwtService.generateToken(adminUser());
        // Flip last character to corrupt the signature
        String tampered = token.substring(0, token.length() - 3) + "xxx";
        assertThat(jwtService.isTokenValid(tampered)).isFalse();
    }

    @Test
    void isTokenValid_emptyString_returnsFalse() {
        assertThat(jwtService.isTokenValid("")).isFalse();
    }

    @Test
    void isTokenValid_garbage_returnsFalse() {
        assertThat(jwtService.isTokenValid("not.a.jwt")).isFalse();
    }

    @Test
    void generateToken_expiredToken_invalidatesCorrectly() {
        ReflectionTestUtils.setField(jwtService, "expirationMs", -1L); // already expired
        String token = jwtService.generateToken(adminUser());
        assertThat(jwtService.isTokenValid(token)).isFalse();
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private static User adminUser() {
        return User.builder()
            .id(UUID.randomUUID())
            .username("alice")
            .email("alice@example.com")
            .passwordHash("hash")
            .role(User.Role.ADMIN)
            .tenantId(UUID.randomUUID())
            .mustChangePassword(false)
            .build();
    }
}

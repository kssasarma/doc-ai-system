package com.docai.bot.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.docai.bot.application.service.ApiKeyService;
import com.docai.bot.application.service.ApiKeyService.ApiKeyDTO;
import com.docai.bot.application.service.ApiKeyService.CreateKeyResult;
import com.docai.bot.domain.entity.ApiKey;
import com.docai.bot.domain.repository.ApiKeyRepository;

@ExtendWith(MockitoExtension.class)
class ApiKeyServiceTest {

    @Mock
    private ApiKeyRepository apiKeyRepository;

    private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();
    private ApiKeyService apiKeyService;

    private static final UUID USER_ID = UUID.randomUUID();
    private static final UUID KEY_ID  = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        apiKeyService = new ApiKeyService(apiKeyRepository, passwordEncoder);
    }

    // ── createKey ─────────────────────────────────────────────────────────────

    @Test
    void createKey_defaultScopes_appliesQueryScope() {
        ArgumentCaptor<ApiKey> saved = ArgumentCaptor.forClass(ApiKey.class);
        when(apiKeyRepository.save(saved.capture())).thenAnswer(inv -> {
            ApiKey k = inv.getArgument(0);
            setId(k, KEY_ID);
            return k;
        });

        CreateKeyResult result = apiKeyService.createKey(USER_ID, "My Key", null, null, null);

        assertThat(result.dto().getScopes()).contains("query");
        assertThat(result.rawKey()).startsWith("sk-docai-");
        assertThat(result.dto().getRateLimitPerMin()).isEqualTo(60);
        assertThat(result.dto().getExpiresAt()).isNull();
    }

    @Test
    void createKey_customScopesAndRateLimit_persisted() {
        ArgumentCaptor<ApiKey> saved = ArgumentCaptor.forClass(ApiKey.class);
        when(apiKeyRepository.save(saved.capture())).thenAnswer(inv -> {
            ApiKey k = inv.getArgument(0);
            setId(k, KEY_ID);
            return k;
        });

        CreateKeyResult result = apiKeyService.createKey(
            USER_ID, "Prod Key", new String[]{"query", "search"}, 100, 30);

        assertThat(saved.getValue().getScopes()).contains("query", "search");
        assertThat(saved.getValue().getRateLimitPerMin()).isEqualTo(100);
        assertThat(saved.getValue().getExpiresAt()).isAfter(LocalDateTime.now());
    }

    @Test
    void createKey_rawKeyIsUniquePerCall() {
        when(apiKeyRepository.save(any())).thenAnswer(inv -> {
            ApiKey k = inv.getArgument(0);
            setId(k, UUID.randomUUID());
            return k;
        });

        String raw1 = apiKeyService.createKey(USER_ID, "Key 1", null, null, null).rawKey();
        String raw2 = apiKeyService.createKey(USER_ID, "Key 2", null, null, null).rawKey();

        assertThat(raw1).isNotEqualTo(raw2);
    }

    @Test
    void createKey_hashStoredNotRaw() {
        ArgumentCaptor<ApiKey> saved = ArgumentCaptor.forClass(ApiKey.class);
        when(apiKeyRepository.save(saved.capture())).thenAnswer(inv -> {
            ApiKey k = inv.getArgument(0);
            setId(k, KEY_ID);
            return k;
        });

        CreateKeyResult result = apiKeyService.createKey(USER_ID, "Secure Key", null, null, null);
        String storedHash = saved.getValue().getKeyHash();

        assertThat(storedHash).isNotEqualTo(result.rawKey());
        assertThat(passwordEncoder.matches(result.rawKey(), storedHash)).isTrue();
    }

    // ── listKeys ──────────────────────────────────────────────────────────────

    @Test
    void listKeys_returnsAllKeysForUser() {
        ApiKey key = activeKey("My Key");
        when(apiKeyRepository.findByUserIdOrderByCreatedAtDesc(USER_ID)).thenReturn(List.of(key));

        List<ApiKeyDTO> keys = apiKeyService.listKeys(USER_ID);

        assertThat(keys).hasSize(1);
        assertThat(keys.get(0).getName()).isEqualTo("My Key");
    }

    @Test
    void listKeys_emptyWhenNoKeys() {
        when(apiKeyRepository.findByUserIdOrderByCreatedAtDesc(USER_ID)).thenReturn(List.of());

        assertThat(apiKeyService.listKeys(USER_ID)).isEmpty();
    }

    // ── revokeKey ─────────────────────────────────────────────────────────────

    @Test
    void revokeKey_ownKey_setsRevoked() {
        ApiKey key = activeKey("Revoke Me");
        when(apiKeyRepository.findById(KEY_ID)).thenReturn(Optional.of(key));
        ArgumentCaptor<ApiKey> saved = ArgumentCaptor.forClass(ApiKey.class);
        when(apiKeyRepository.save(saved.capture())).thenReturn(key);

        apiKeyService.revokeKey(KEY_ID, USER_ID);

        assertThat(saved.getValue().isRevoked()).isTrue();
    }

    @Test
    void revokeKey_anotherUsersKey_throwsIllegalArgument() {
        ApiKey key = activeKey("Others Key");
        key.setUserId(UUID.randomUUID()); // different owner
        when(apiKeyRepository.findById(KEY_ID)).thenReturn(Optional.of(key));

        assertThatThrownBy(() -> apiKeyService.revokeKey(KEY_ID, USER_ID))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Not authorized");
    }

    @Test
    void revokeKeyAsAdmin_anyKey_setsRevoked() {
        ApiKey key = activeKey("Someone's Key");
        key.setUserId(UUID.randomUUID()); // different owner, but admin can revoke
        when(apiKeyRepository.findById(KEY_ID)).thenReturn(Optional.of(key));
        ArgumentCaptor<ApiKey> saved = ArgumentCaptor.forClass(ApiKey.class);
        when(apiKeyRepository.save(saved.capture())).thenReturn(key);

        apiKeyService.revokeKeyAsAdmin(KEY_ID);

        assertThat(saved.getValue().isRevoked()).isTrue();
    }

    // ── validateKey ───────────────────────────────────────────────────────────

    @Test
    void validateKey_validActiveKey_returnsKey() {
        String rawToken = "sk-docai-validtesttoken00";
        String prefix = rawToken.substring(0, 12);
        ApiKey key = ApiKey.builder()
            .userId(USER_ID)
            .keyHash(passwordEncoder.encode(rawToken))
            .keyPrefix(prefix)
            .name("Test Key")
            .scopes(new String[]{"query"})
            .rateLimitPerMin(60)
            .revoked(false)
            .build();
        setId(key, KEY_ID);

        when(apiKeyRepository.findByKeyPrefix(prefix)).thenReturn(List.of(key));

        Optional<ApiKey> result = apiKeyService.validateKey(rawToken);
        assertThat(result).isPresent();
    }

    @Test
    void validateKey_revokedKey_returnsEmpty() {
        String rawToken = "sk-docai-revokedtoken00";
        String prefix = rawToken.substring(0, 12);
        ApiKey key = ApiKey.builder()
            .userId(USER_ID)
            .keyHash(passwordEncoder.encode(rawToken))
            .keyPrefix(prefix)
            .name("Revoked Key")
            .scopes(new String[]{"query"})
            .rateLimitPerMin(60)
            .revoked(true)
            .build();
        setId(key, KEY_ID);

        when(apiKeyRepository.findByKeyPrefix(prefix)).thenReturn(List.of(key));

        assertThat(apiKeyService.validateKey(rawToken)).isEmpty();
    }

    @Test
    void validateKey_tooShort_returnsEmpty() {
        assertThat(apiKeyService.validateKey("short")).isEmpty();
    }

    @Test
    void validateKey_null_returnsEmpty() {
        assertThat(apiKeyService.validateKey(null)).isEmpty();
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private ApiKey activeKey(String name) {
        ApiKey key = ApiKey.builder()
            .userId(USER_ID)
            .keyHash("$2a$10$hashedvalue")
            .keyPrefix("sk-docai-test")
            .name(name)
            .scopes(new String[]{"query"})
            .rateLimitPerMin(60)
            .revoked(false)
            .build();
        setId(key, KEY_ID);
        return key;
    }

    private static void setId(ApiKey key, UUID id) {
        try {
            var field = key.getClass().getDeclaredField("id");
            field.setAccessible(true);
            field.set(key, id);
        } catch (Exception ignored) {}
    }
}

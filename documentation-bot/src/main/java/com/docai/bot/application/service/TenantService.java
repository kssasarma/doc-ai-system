package com.docai.bot.application.service;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.docai.bot.domain.entity.DataRetentionPolicy;
import com.docai.bot.domain.entity.Tenant;
import com.docai.bot.domain.entity.TenantBranding;
import com.docai.bot.domain.entity.TenantLLMConfig;
import com.docai.bot.domain.entity.TenantStorageConfig;
import com.docai.bot.domain.repository.DataRetentionPolicyRepository;
import com.docai.bot.domain.repository.DocumentRepository;
import com.docai.bot.domain.repository.SharedChatLinkRepository;
import com.docai.bot.domain.repository.TenantBrandingRepository;
import com.docai.bot.domain.repository.TenantLLMConfigRepository;
import com.docai.bot.domain.repository.TenantRepository;
import com.docai.bot.domain.repository.TenantStorageConfigRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class TenantService {

    private final TenantRepository tenantRepository;
    private final TenantBrandingRepository brandingRepository;
    private final TenantLLMConfigRepository llmConfigRepository;
    private final TenantStorageConfigRepository storageConfigRepository;
    private final DataRetentionPolicyRepository retentionRepository;
    private final SharedChatLinkRepository sharedChatLinkRepository;
    private final DocumentRepository documentRepository;
    private final List<LLMProvider> llmProviders;
    private final SecretsCryptoService cryptoService;

    public record TenantWithDocCount(Tenant tenant, long documentCount) {}

    /** API keys never leave the server once saved — this is what the admin UI/API actually reads.
     * {@code hasChatKey}/{@code hasEmbeddingKey} + last-4 hints stand in for the keys themselves.
     * A tenant with no chat key has AI features disabled entirely: there is no platform fallback. */
    public record LlmConfigView(String chatProvider, String chatModel, String chatBaseUrl,
                                 String embeddingProvider, String embeddingModel, String embeddingBaseUrl,
                                 boolean routingEnabled, String simpleModel, String complexModel,
                                 String rerankModel,
                                 String azureEndpoint, String azureDeployment,
                                 boolean hasChatKey, String chatKeyHint,
                                 boolean hasEmbeddingKey, String embeddingKeyHint,
                                 double temperature, int maxTokens, Integer maxEmbeddingBatchTokens) {}

    /** {@code apiKey}/{@code embeddingApiKey}: null = leave the stored key untouched, "" = clear it,
     * non-blank = set/replace it. {@code maxEmbeddingBatchTokens}: null = use the ingestor's
     * built-in default for this tenant. Base URLs: null/blank = the provider's canonical public
     * endpoint. {@code rerankModel}: null/blank = inherit (simpleModel when routing is enabled,
     * else chatModel) — same semantics as the base URL fields. */
    public record LlmConfigUpdate(String chatProvider, String chatModel, String chatBaseUrl,
                                   String embeddingProvider, String embeddingModel, String embeddingBaseUrl,
                                   boolean routingEnabled, String simpleModel, String complexModel,
                                   String rerankModel,
                                   String azureEndpoint, String azureDeployment,
                                   String apiKey, String embeddingApiKey,
                                   Double temperature, Integer maxTokens, Integer maxEmbeddingBatchTokens) {}

    public record TestConnectionResult(boolean success, String message) {}

    /**
     * Safe view returned to the admin UI — credentials are never echoed back. The access key and
     * secret key are represented only by a last-4-chars hint (same pattern as LLM API keys).
     */
    public record StorageConfigView(String s3Bucket, String s3Region, String s3Endpoint,
                                     boolean s3PathStyleAccess,
                                     boolean hasAccessKey, String accessKeyHint,
                                     boolean hasSecretKey, String secretKeyHint) {}

    /**
     * Null credentials = leave stored value untouched. Empty string = clear. Non-blank = set/replace.
     * This matches the same null-means-unchanged convention as {@link LlmConfigUpdate}.
     */
    public record StorageConfigUpdate(String s3Bucket, String s3Region, String s3Endpoint,
                                       boolean s3PathStyleAccess,
                                       String accessKey, String secretKey) {}

    public List<Tenant> listAll() {
        return tenantRepository.findAll();
    }

    public List<TenantWithDocCount> listAllWithDocCounts() {
        List<Tenant> tenants = tenantRepository.findAll();
        Map<UUID, Long> countsByTenant = documentRepository.countDocumentsPerTenant()
            .stream().collect(Collectors.toMap(
                DocumentRepository.TenantDocCount::getTenantId,
                DocumentRepository.TenantDocCount::getCount));
        return tenants.stream()
            .map(t -> new TenantWithDocCount(t, countsByTenant.getOrDefault(t.getId(), 0L)))
            .toList();
    }

    public Tenant getById(UUID id) {
        return tenantRepository.findById(id)
            .orElseThrow(() -> new IllegalArgumentException("Tenant not found: " + id));
    }

    @Transactional
    public Tenant create(String name, String slug, String plan, int maxUsers, int maxDocuments) {
        if (tenantRepository.existsBySlug(slug)) {
            throw new IllegalArgumentException("Slug already taken: " + slug);
        }
        Tenant tenant = tenantRepository.save(Tenant.builder()
            .name(name).slug(slug).plan(plan)
            .maxUsers(maxUsers).maxDocuments(maxDocuments)
            .build());

        // Seed defaults
        brandingRepository.save(TenantBranding.builder().tenantId(tenant.getId()).build());
        llmConfigRepository.save(TenantLLMConfig.builder().tenantId(tenant.getId()).build());
        retentionRepository.save(DataRetentionPolicy.builder().tenantId(tenant.getId()).build());

        return tenant;
    }

    @Transactional
    public Tenant update(UUID id, String name, String plan, boolean active, int maxUsers, int maxDocuments) {
        Tenant tenant = getById(id);
        boolean isDeactivating = tenant.isActive() && !active;

        tenant.setName(name);
        tenant.setPlan(plan);
        tenant.setActive(active);
        tenant.setMaxUsers(maxUsers);
        tenant.setMaxDocuments(maxDocuments);
        Tenant saved = tenantRepository.save(tenant);

        if (isDeactivating) {
            sharedChatLinkRepository.deleteByTenantId(id);
            log.info("Tenant {} deactivated — revoked all of its chat-share links", id);
        }

        return saved;
    }

    public TenantBranding getBranding(UUID tenantId) {
        return brandingRepository.findByTenantId(tenantId)
            .orElseGet(() -> TenantBranding.builder().tenantId(tenantId).build());
    }

    @Transactional
    public TenantBranding updateBranding(UUID tenantId, TenantBranding update) {
        TenantBranding branding = brandingRepository.findByTenantId(tenantId)
            .orElseGet(() -> TenantBranding.builder().tenantId(tenantId).build());
        branding.setProductName(update.getProductName());
        branding.setLogoUrl(update.getLogoUrl());
        branding.setFaviconUrl(update.getFaviconUrl());
        branding.setPrimaryColor(update.getPrimaryColor());
        branding.setAccentColor(update.getAccentColor());
        branding.setCustomCss(update.getCustomCss());
        branding.setSupportEmail(update.getSupportEmail());
        branding.setFooterText(update.getFooterText());
        return brandingRepository.save(branding);
    }

    public LlmConfigView getLLMConfig(UUID tenantId) {
        TenantLLMConfig config = llmConfigRepository.findByTenantId(tenantId)
            .orElseGet(() -> TenantLLMConfig.builder().tenantId(tenantId).build());
        return toView(config);
    }

    @Transactional
    public LlmConfigView updateLLMConfig(UUID tenantId, LlmConfigUpdate update) {
        // Reject an unregistered provider here instead of letting an admin "configure" a provider
        // that LLMRouter would refuse at call time.
        Set<String> validProviders = llmProviders.stream()
            .map(LLMProvider::providerName).collect(Collectors.toSet());
        if (!validProviders.contains(update.chatProvider())) {
            throw new IllegalArgumentException(
                "Unknown chat provider '" + update.chatProvider() + "' — must be one of " + validProviders);
        }
        if (!validProviders.contains(update.embeddingProvider())) {
            throw new IllegalArgumentException(
                "Unknown embedding provider '" + update.embeddingProvider() + "' — must be one of " + validProviders);
        }
        llmProviders.stream()
            .filter(p -> p.providerName().equals(update.embeddingProvider()) && !p.supportsEmbeddings())
            .findAny()
            .ifPresent(p -> {
                throw new IllegalArgumentException("Provider '" + p.providerName()
                    + "' has no embedding API — choose one that does (e.g. openai) as the embedding provider");
            });
        if (update.maxEmbeddingBatchTokens() != null && update.maxEmbeddingBatchTokens() <= 0) {
            throw new IllegalArgumentException("maxEmbeddingBatchTokens must be positive, or null for the default");
        }
        if (update.temperature() != null && (update.temperature() < 0 || update.temperature() > 2)) {
            throw new IllegalArgumentException("temperature must be between 0 and 2");
        }
        if (update.maxTokens() != null && update.maxTokens() <= 0) {
            throw new IllegalArgumentException("maxTokens must be positive");
        }
        validateBaseUrl(update.chatBaseUrl(), "chatBaseUrl");
        validateBaseUrl(update.embeddingBaseUrl(), "embeddingBaseUrl");

        TenantLLMConfig config = llmConfigRepository.findByTenantId(tenantId)
            .orElseGet(() -> TenantLLMConfig.builder().tenantId(tenantId).build());
        config.setChatProvider(update.chatProvider());
        config.setChatModel(update.chatModel());
        config.setChatBaseUrl(blankToNull(update.chatBaseUrl()));
        config.setEmbeddingProvider(update.embeddingProvider());
        config.setEmbeddingModel(update.embeddingModel());
        config.setEmbeddingBaseUrl(blankToNull(update.embeddingBaseUrl()));
        config.setRoutingEnabled(update.routingEnabled());
        config.setSimpleModel(update.simpleModel());
        config.setComplexModel(update.complexModel());
        config.setRerankModel(blankToNull(update.rerankModel()));
        config.setMaxEmbeddingBatchTokens(update.maxEmbeddingBatchTokens());
        if (update.temperature() != null) config.setTemperature(update.temperature());
        if (update.maxTokens() != null) config.setMaxTokens(update.maxTokens());
        if (update.azureEndpoint() != null) config.setAzureEndpoint(update.azureEndpoint());
        if (update.azureDeployment() != null) config.setAzureDeployment(update.azureDeployment());

        // Keys: null = leave stored key untouched, "" = clear it, non-blank = set/replace it.
        if (update.apiKey() != null) {
            config.setApiKeyEnc(update.apiKey().isBlank() ? null : cryptoService.encrypt(update.apiKey()));
        }
        if (update.embeddingApiKey() != null) {
            config.setEmbeddingApiKeyEnc(update.embeddingApiKey().isBlank()
                ? null : cryptoService.encrypt(update.embeddingApiKey()));
        }

        return toView(llmConfigRepository.save(config));
    }

    private static void validateBaseUrl(String url, String field) {
        if (url == null || url.isBlank()) return;
        if (!url.startsWith("https://") && !url.startsWith("http://")) {
            throw new IllegalArgumentException(field + " must be an http(s) URL");
        }
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s;
    }

    /** Tests a chat provider/model/key/endpoint combination without persisting anything — used by
     * the admin UI's "Test connection" action before saving. Empty apiKey means "use whatever key
     * is already stored for this tenant" (matches updateLLMConfig's null-apiKey semantics) so an
     * admin can re-verify an already-saved key. There is no platform key to fall back to: with
     * nothing entered and nothing stored, the test fails with an actionable message. */
    public TestConnectionResult testConnection(UUID tenantId, String provider, String model,
                                                String apiKey, String baseUrl) {
        LLMProvider llmProvider = llmProviders.stream()
            .filter(p -> p.providerName().equals(provider))
            .findFirst()
            .orElse(null);
        if (llmProvider == null) {
            return new TestConnectionResult(false, "Unknown provider '" + provider + "'");
        }
        String effectiveKey = (apiKey != null && !apiKey.isBlank()) ? apiKey : decryptStoredKey(tenantId);
        if (effectiveKey == null) {
            return new TestConnectionResult(false,
                "No API key to test — enter one above or save one first (there is no platform default key)");
        }
        TenantLLMConfig stored = llmConfigRepository.findByTenantId(tenantId).orElse(null);
        String effectiveBaseUrl = (baseUrl != null && !baseUrl.isBlank())
            ? baseUrl : (stored != null ? stored.getChatBaseUrl() : null);
        double temperature = stored != null ? stored.getTemperature() : 0.7;
        try {
            var response = llmProvider.chat(null, "Reply with exactly one word: OK",
                new LLMProvider.ChatSettings(model, effectiveKey, effectiveBaseUrl, temperature, 16));
            String text = response != null && response.getResult() != null
                ? response.getResult().getOutput().getText() : null;
            if (text == null || text.isBlank()) {
                return new TestConnectionResult(false, "Provider returned an empty response");
            }
            return new TestConnectionResult(true, "Connected successfully to " + provider + "/" + model);
        } catch (Exception e) {
            String message = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
            log.warn("LLM connection test failed for tenant {} ({}/{}): {}", tenantId, provider, model, message);
            return new TestConnectionResult(false, message);
        }
    }

    private String decryptStoredKey(UUID tenantId) {
        return llmConfigRepository.findByTenantId(tenantId)
            .map(TenantLLMConfig::getApiKeyEnc)
            .filter(enc -> enc != null && !enc.isBlank())
            .map(cryptoService::decrypt)
            .orElse(null);
    }

    private LlmConfigView toView(TenantLLMConfig config) {
        boolean hasChatKey = config.getApiKeyEnc() != null && !config.getApiKeyEnc().isBlank();
        boolean hasEmbeddingKey = config.getEmbeddingApiKeyEnc() != null && !config.getEmbeddingApiKeyEnc().isBlank();
        return new LlmConfigView(config.getChatProvider(), config.getChatModel(), config.getChatBaseUrl(),
            config.getEmbeddingProvider(), config.getEmbeddingModel(), config.getEmbeddingBaseUrl(),
            config.isRoutingEnabled(), config.getSimpleModel(), config.getComplexModel(), config.getRerankModel(),
            config.getAzureEndpoint(), config.getAzureDeployment(),
            hasChatKey, keyHint(config.getApiKeyEnc()),
            hasEmbeddingKey, keyHint(config.getEmbeddingApiKeyEnc()),
            config.getTemperature(), config.getMaxTokens(), config.getMaxEmbeddingBatchTokens());
    }

    private String keyHint(String encrypted) {
        if (encrypted == null || encrypted.isBlank()) return null;
        String decrypted = cryptoService.decrypt(encrypted);
        return decrypted != null && decrypted.length() >= 4
            ? "••••" + decrypted.substring(decrypted.length() - 4)
            : "••••";
    }

    public StorageConfigView getStorageConfig(UUID tenantId) {
        return storageConfigRepository.findByTenantId(tenantId)
            .map(this::toStorageView)
            .orElseGet(() -> new StorageConfigView(null, null, null, false, false, null, false, null));
    }

    @Transactional
    public StorageConfigView updateStorageConfig(UUID tenantId, StorageConfigUpdate update) {
        if (update.s3Bucket() == null || update.s3Bucket().isBlank()) {
            throw new IllegalArgumentException("s3Bucket is required");
        }
        if (update.s3Region() == null || update.s3Region().isBlank()) {
            throw new IllegalArgumentException("s3Region is required");
        }
        if (update.s3Endpoint() != null && !update.s3Endpoint().isBlank()) {
            validateBaseUrl(update.s3Endpoint(), "s3Endpoint");
        }

        TenantStorageConfig config = storageConfigRepository.findByTenantId(tenantId)
            .orElseGet(() -> TenantStorageConfig.builder().tenantId(tenantId)
                .s3AccessKeyEnc("").s3SecretKeyEnc("").build());

        config.setS3Bucket(update.s3Bucket());
        config.setS3Region(update.s3Region());
        config.setS3Endpoint(blankToNull(update.s3Endpoint()));
        config.setS3PathStyleAccess(update.s3PathStyleAccess());

        // null = leave stored key untouched, "" = clear, non-blank = encrypt and store
        if (update.accessKey() != null) {
            config.setS3AccessKeyEnc(update.accessKey().isBlank() ? "" : cryptoService.encrypt(update.accessKey()));
        }
        if (update.secretKey() != null) {
            config.setS3SecretKeyEnc(update.secretKey().isBlank() ? "" : cryptoService.encrypt(update.secretKey()));
        }

        return toStorageView(storageConfigRepository.save(config));
    }

    private StorageConfigView toStorageView(TenantStorageConfig config) {
        boolean hasAccessKey = config.getS3AccessKeyEnc() != null && !config.getS3AccessKeyEnc().isBlank();
        boolean hasSecretKey = config.getS3SecretKeyEnc() != null && !config.getS3SecretKeyEnc().isBlank();
        return new StorageConfigView(
            config.getS3Bucket(), config.getS3Region(), config.getS3Endpoint(),
            config.isS3PathStyleAccess(),
            hasAccessKey, keyHint(config.getS3AccessKeyEnc()),
            hasSecretKey, keyHint(config.getS3SecretKeyEnc()));
    }

    public DataRetentionPolicy getRetentionPolicy(UUID tenantId) {
        return retentionRepository.findByTenantId(tenantId)
            .orElseGet(() -> DataRetentionPolicy.builder().tenantId(tenantId).build());
    }

    @Transactional
    public DataRetentionPolicy updateRetentionPolicy(UUID tenantId, DataRetentionPolicy update) {
        DataRetentionPolicy policy = retentionRepository.findByTenantId(tenantId)
            .orElseGet(() -> DataRetentionPolicy.builder().tenantId(tenantId).build());
        policy.setQueryLogDays(update.getQueryLogDays());
        policy.setChatSessionDays(update.getChatSessionDays());
        policy.setAuditLogDays(update.getAuditLogDays());
        policy.setFeedbackDays(update.getFeedbackDays());
        return retentionRepository.save(policy);
    }
}

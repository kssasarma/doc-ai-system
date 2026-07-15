package com.docai.bot.application.service;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.docai.bot.domain.entity.DataRetentionPolicy;
import com.docai.bot.domain.entity.Tenant;
import com.docai.bot.domain.entity.TenantBranding;
import com.docai.bot.domain.entity.TenantLLMConfig;
import com.docai.bot.domain.repository.DataRetentionPolicyRepository;
import com.docai.bot.domain.repository.SharedChatLinkRepository;
import com.docai.bot.domain.repository.TenantBrandingRepository;
import com.docai.bot.domain.repository.TenantLLMConfigRepository;
import com.docai.bot.domain.repository.TenantRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class TenantService {

    private final TenantRepository tenantRepository;
    private final TenantBrandingRepository brandingRepository;
    private final TenantLLMConfigRepository llmConfigRepository;
    private final DataRetentionPolicyRepository retentionRepository;
    private final SharedChatLinkRepository sharedChatLinkRepository;
    private final List<LLMProvider> llmProviders;
    private final SecretsCryptoService cryptoService;

    /** API key never leaves the server once saved — this is what the admin UI/API actually reads. */
    public record LlmConfigView(String chatProvider, String chatModel, String embeddingProvider,
                                 String embeddingModel, boolean routingEnabled, String simpleModel,
                                 String complexModel, String azureEndpoint, String azureDeployment,
                                 boolean hasCustomKey, String keyHint) {}

    /** {@code apiKey}: null = leave the stored key untouched, "" = clear it, non-blank = set/replace it. */
    public record LlmConfigUpdate(String chatProvider, String chatModel, String embeddingProvider,
                                   String embeddingModel, boolean routingEnabled, String simpleModel,
                                   String complexModel, String azureEndpoint, String azureDeployment,
                                   String apiKey) {}

    public record TestConnectionResult(boolean success, String message) {}

    public List<Tenant> listAll() {
        return tenantRepository.findAll();
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
        // LLMRouter silently falls back to OpenAI for any provider name it doesn't recognize —
        // reject an unregistered one here instead of letting an admin "configure" a provider
        // that's actually never used.
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

        TenantLLMConfig config = llmConfigRepository.findByTenantId(tenantId)
            .orElseGet(() -> TenantLLMConfig.builder().tenantId(tenantId).build());
        config.setChatProvider(update.chatProvider());
        config.setChatModel(update.chatModel());
        config.setEmbeddingProvider(update.embeddingProvider());
        config.setEmbeddingModel(update.embeddingModel());
        config.setRoutingEnabled(update.routingEnabled());
        config.setSimpleModel(update.simpleModel());
        config.setComplexModel(update.complexModel());
        if (update.azureEndpoint() != null) config.setAzureEndpoint(update.azureEndpoint());
        if (update.azureDeployment() != null) config.setAzureDeployment(update.azureDeployment());

        // apiKey: null = leave stored key untouched, "" = clear it, non-blank = set/replace it.
        if (update.apiKey() != null) {
            config.setApiKeyEnc(update.apiKey().isBlank() ? null : cryptoService.encrypt(update.apiKey()));
        }

        return toView(llmConfigRepository.save(config));
    }

    /** Tests a chat provider/model/key combination without persisting anything — used by the
     * admin UI's "Test connection" action before saving. Empty apiKey means "use whatever key is
     * already stored for this tenant, or the platform default" (matches updateLLMConfig's null-
     * apiKey semantics) so an admin can re-verify an already-saved custom key. */
    public TestConnectionResult testConnection(UUID tenantId, String provider, String model, String apiKey) {
        LLMProvider llmProvider = llmProviders.stream()
            .filter(p -> p.providerName().equals(provider))
            .findFirst()
            .orElse(null);
        if (llmProvider == null) {
            return new TestConnectionResult(false, "Unknown provider '" + provider + "'");
        }
        String effectiveKey = (apiKey != null && !apiKey.isBlank()) ? apiKey : decryptStoredKey(tenantId);
        try {
            var response = llmProvider.chat(null,
                "Reply with exactly one word: OK", model, effectiveKey);
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
        boolean hasCustomKey = config.getApiKeyEnc() != null && !config.getApiKeyEnc().isBlank();
        String hint = null;
        if (hasCustomKey) {
            String decrypted = cryptoService.decrypt(config.getApiKeyEnc());
            hint = decrypted != null && decrypted.length() >= 4
                ? "••••" + decrypted.substring(decrypted.length() - 4)
                : "••••";
        }
        return new LlmConfigView(config.getChatProvider(), config.getChatModel(),
            config.getEmbeddingProvider(), config.getEmbeddingModel(), config.isRoutingEnabled(),
            config.getSimpleModel(), config.getComplexModel(), config.getAzureEndpoint(),
            config.getAzureDeployment(), hasCustomKey, hint);
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

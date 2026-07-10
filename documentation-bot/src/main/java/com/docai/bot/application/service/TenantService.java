package com.docai.bot.application.service;

import java.util.List;
import java.util.UUID;

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

    public TenantLLMConfig getLLMConfig(UUID tenantId) {
        return llmConfigRepository.findByTenantId(tenantId)
            .orElseGet(() -> TenantLLMConfig.builder().tenantId(tenantId).build());
    }

    @Transactional
    public TenantLLMConfig updateLLMConfig(UUID tenantId, TenantLLMConfig update) {
        TenantLLMConfig config = llmConfigRepository.findByTenantId(tenantId)
            .orElseGet(() -> TenantLLMConfig.builder().tenantId(tenantId).build());
        config.setChatProvider(update.getChatProvider());
        config.setChatModel(update.getChatModel());
        config.setEmbeddingProvider(update.getEmbeddingProvider());
        config.setEmbeddingModel(update.getEmbeddingModel());
        config.setRoutingEnabled(update.isRoutingEnabled());
        config.setSimpleModel(update.getSimpleModel());
        config.setComplexModel(update.getComplexModel());
        if (update.getAzureEndpoint() != null) config.setAzureEndpoint(update.getAzureEndpoint());
        if (update.getAzureDeployment() != null) config.setAzureDeployment(update.getAzureDeployment());
        return llmConfigRepository.save(config);
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

package com.docai.bot.adapter.rest;

import java.util.List;
import java.util.UUID;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.docai.bot.application.service.TenantService;
import com.docai.bot.domain.entity.DataRetentionPolicy;
import com.docai.bot.domain.entity.Tenant;
import com.docai.bot.domain.entity.TenantBranding;
import com.docai.bot.domain.entity.TenantLLMConfig;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/admin/tenants")
@RequiredArgsConstructor
public class TenantController {

    private final TenantService tenantService;

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public List<Tenant> listAll() {
        return tenantService.listAll();
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public Tenant getById(@PathVariable UUID id) {
        return tenantService.getById(id);
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public Tenant create(@RequestBody CreateTenantRequest req) {
        return tenantService.create(req.name(), req.slug(), req.plan(),
            req.maxUsers() > 0 ? req.maxUsers() : 10,
            req.maxDocuments() > 0 ? req.maxDocuments() : 100);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public Tenant update(@PathVariable UUID id, @RequestBody UpdateTenantRequest req) {
        return tenantService.update(id, req.name(), req.plan(), req.active(),
            req.maxUsers(), req.maxDocuments());
    }

    @GetMapping("/{id}/branding")
    @PreAuthorize("hasRole('ADMIN')")
    public TenantBranding getBranding(@PathVariable UUID id) {
        return tenantService.getBranding(id);
    }

    @PutMapping("/{id}/branding")
    @PreAuthorize("hasRole('ADMIN')")
    public TenantBranding updateBranding(@PathVariable UUID id, @RequestBody TenantBranding body) {
        return tenantService.updateBranding(id, body);
    }

    @GetMapping("/{id}/llm-config")
    @PreAuthorize("hasRole('ADMIN')")
    public TenantLLMConfig getLLMConfig(@PathVariable UUID id) {
        return tenantService.getLLMConfig(id);
    }

    @PutMapping("/{id}/llm-config")
    @PreAuthorize("hasRole('ADMIN')")
    public TenantLLMConfig updateLLMConfig(@PathVariable UUID id, @RequestBody TenantLLMConfig body) {
        return tenantService.updateLLMConfig(id, body);
    }

    @GetMapping("/{id}/retention")
    @PreAuthorize("hasRole('ADMIN')")
    public DataRetentionPolicy getRetention(@PathVariable UUID id) {
        return tenantService.getRetentionPolicy(id);
    }

    @PutMapping("/{id}/retention")
    @PreAuthorize("hasRole('ADMIN')")
    public DataRetentionPolicy updateRetention(@PathVariable UUID id, @RequestBody DataRetentionPolicy body) {
        return tenantService.updateRetentionPolicy(id, body);
    }

    record CreateTenantRequest(String name, String slug, String plan, int maxUsers, int maxDocuments) {}

    record UpdateTenantRequest(String name, String plan, boolean active, int maxUsers, int maxDocuments) {}
}

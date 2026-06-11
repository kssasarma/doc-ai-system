package com.docai.bot.adapter.rest;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.docai.bot.application.service.TenantService;
import com.docai.bot.config.TenantContext;
import com.docai.bot.domain.entity.TenantBranding;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/branding")
@RequiredArgsConstructor
public class BrandingController {

    private final TenantService tenantService;

    /** Public — no auth required. Returns branding config for the resolved tenant. */
    @GetMapping
    public TenantBranding getBranding() {
        return tenantService.getBranding(TenantContext.get());
    }
}

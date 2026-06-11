package com.docai.bot.config;

import java.io.IOException;
import java.util.UUID;

import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import com.docai.bot.application.service.JwtService;
import com.docai.bot.domain.repository.TenantRepository;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Resolves the current tenant from (in priority order):
 * 1. X-Tenant-Id header (UUID) — for inter-service or API-key calls that already know the tenant
 * 2. X-Tenant-Slug header — resolves slug → UUID via DB
 * 3. JWT claim "tenantId" — for browser sessions
 * 4. Falls back to the default tenant when none of the above are present
 */
@Slf4j
@Component
@Order(1)
@RequiredArgsConstructor
public class TenantResolutionFilter extends OncePerRequestFilter {

    private final JwtService jwtService;
    private final TenantRepository tenantRepository;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain)
            throws ServletException, IOException {
        try {
            UUID tenantId = resolve(request);
            TenantContext.set(tenantId);
            chain.doFilter(request, response);
        } finally {
            TenantContext.clear();
        }
    }

    private UUID resolve(HttpServletRequest request) {
        // 1. Explicit UUID header
        String idHeader = request.getHeader("X-Tenant-Id");
        if (StringUtils.hasText(idHeader)) {
            try {
                return UUID.fromString(idHeader.trim());
            } catch (IllegalArgumentException ignored) {}
        }

        // 2. Slug header
        String slugHeader = request.getHeader("X-Tenant-Slug");
        if (StringUtils.hasText(slugHeader)) {
            return tenantRepository.findBySlug(slugHeader.trim())
                .map(t -> t.getId())
                .orElseGet(() -> TenantContext.get());
        }

        // 3. JWT claim
        String authHeader = request.getHeader("Authorization");
        if (StringUtils.hasText(authHeader) && authHeader.startsWith("Bearer ")) {
            String token = authHeader.substring(7);
            try {
                String claim = jwtService.extractAllClaims(token).get("tenantId", String.class);
                if (StringUtils.hasText(claim)) {
                    return UUID.fromString(claim);
                }
            } catch (Exception ignored) {}
        }

        return TenantContext.get(); // default
    }
}

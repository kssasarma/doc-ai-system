package com.docai.bot.config;

import java.io.IOException;
import java.util.UUID;

import org.springframework.core.annotation.Order;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import com.docai.bot.domain.repository.TenantRepository;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Resolves the current tenant from (in priority order):
 * 1. The already-authenticated principal's own tenantId (JWT or API key — see SecurityConfig,
 *    which deliberately runs this filter AFTER both auth filters so their result is available
 *    here via SecurityContextHolder). Authoritative and never overridden by anything below: an
 *    authenticated caller cannot pick a different tenant to act as just by sending a header —
 *    that was a real cross-tenant IDOR (a spoofed X-Tenant-Id let any ADMIN read another
 *    tenant's whole document corpus via GrantBasedDocumentAccessPolicy).
 * 2. X-Tenant-Id header (UUID) — only consulted with no authenticated principal, e.g. a
 *    pre-login public branding/FAQ lookup that needs a tenant hint.
 * 3. X-Tenant-Slug header — same, resolved via DB.
 *
 * If none resolve, TenantContext is left unset — there is no default/fallback tenant.
 * {@link TenantContext#get()} fails closed for any handler that requires a real tenant;
 * {@link TenantContext#getOrNull()} is available for the few legitimately tenant-optional endpoints.
 */
@Slf4j
@Component
@Order(1)
@RequiredArgsConstructor
public class TenantResolutionFilter extends OncePerRequestFilter {

    private final TenantRepository tenantRepository;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain)
            throws ServletException, IOException {
        try {
            UUID tenantId = resolve(request);
            if (tenantId != null) {
                TenantContext.set(tenantId);
            }
            chain.doFilter(request, response);
        } finally {
            TenantContext.clear();
        }
    }

    private UUID resolve(HttpServletRequest request) {
        UUID fromPrincipal = resolveFromAuthenticatedPrincipal();
        if (fromPrincipal != null) {
            return fromPrincipal;
        }

        // No authenticated identity for this request — safe to take a client-supplied hint,
        // since there's no real tenant membership here to spoof away from.
        String idHeader = request.getHeader("X-Tenant-Id");
        if (StringUtils.hasText(idHeader)) {
            try {
                return UUID.fromString(idHeader.trim());
            } catch (IllegalArgumentException ignored) {}
        }

        String slugHeader = request.getHeader("X-Tenant-Slug");
        if (StringUtils.hasText(slugHeader)) {
            return tenantRepository.findBySlug(slugHeader.trim())
                .map(t -> t.getId())
                .orElse(null);
        }

        return null; // unresolved — no default tenant
    }

    private UUID resolveFromAuthenticatedPrincipal() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.isAuthenticated() && auth.getPrincipal() instanceof UserPrincipal principal) {
            return principal.tenantId(); // null for SUPER_ADMIN — intentionally platform-wide
        }
        return null;
    }
}

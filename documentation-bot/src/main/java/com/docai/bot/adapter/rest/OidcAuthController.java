package com.docai.bot.adapter.rest;

import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.docai.bot.application.service.OidcJitProvisioningService;
import com.docai.bot.domain.repository.TenantRepository;

import lombok.RequiredArgsConstructor;

/**
 * Accepts an already-validated OIDC ID-token (verified by the frontend via the IdP SDK)
 * and exchanges it for an app JWT.  The frontend flow:
 *   1. User authenticates with IdP (Google, Azure, Okta, …)
 *   2. IdP returns an ID-token (already verified client-side)
 *   3. Frontend POSTs decoded claims here
 *   4. We JIT-provision the user and return our own JWT
 *
 * For a production deployment you would verify the token server-side using
 * Spring Security OAuth2 Resource Server or the Google/Azure SDK.
 */
@RestController
@RequestMapping("/api/auth/oidc")
@RequiredArgsConstructor
public class OidcAuthController {

    private final OidcJitProvisioningService provisioningService;
    private final TenantRepository tenantRepository;

    /**
     * POST /api/auth/oidc/callback
     * Body: { "provider": "google", claims: { sub, email, name, picture, … } }
     */
    @PostMapping("/callback")
    public ResponseEntity<TokenResponse> callback(@RequestBody OidcCallbackRequest body) {
        String token = provisioningService.provisionAndIssueToken(body.provider(), body.claims());
        return ResponseEntity.ok(new TokenResponse(token));
    }

    /** Returns the OIDC configuration for a given tenant slug (for the frontend login page). */
    @PostMapping("/config")
    public ResponseEntity<OidcConfig> getConfig(@RequestParam String slug) {
        return tenantRepository.findBySlug(slug)
            .filter(t -> t.isOidcEnabled())
            .map(t -> ResponseEntity.ok(new OidcConfig(
                t.getOidcProvider(), t.getOidcIssuer(), t.getOidcClientId())))
            .orElse(ResponseEntity.notFound().build());
    }

    record OidcCallbackRequest(String provider, Map<String, Object> claims) {}
    record TokenResponse(String token) {}
    record OidcConfig(String provider, String issuerUrl, String clientId) {}
}

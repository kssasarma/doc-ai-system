package com.docai.bot.application.service;

import java.util.Map;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.docai.bot.config.TenantContext;
import com.docai.bot.domain.entity.User;
import com.docai.bot.domain.repository.TenantMembershipRepository;
import com.docai.bot.domain.repository.UserRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Just-in-Time (JIT) provisioning: creates a local User row the first time an OIDC user logs in.
 * Subsequent logins update the display name / avatar from the IdP claims.
 *
 * <p>An OIDC identity (same {@code sub}+{@code provider}) can be JIT-provisioned into more than
 * one tenant — each has its own OIDC config, so logging in via tenant B's IdP after already
 * having an account from tenant A means "this identity also belongs to tenant B now," not "reuse
 * the stale tenant-A row unchanged." Each such login also makes the tenant just logged into the
 * active one, mirroring the fact that OIDC login is inherently tenant-specific.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OidcJitProvisioningService {

    private final UserRepository userRepository;
    private final TenantMembershipRepository membershipRepository;
    private final TenantMembershipService membershipService;
    private final JwtService jwtService;

    /**
     * Called after a successful OIDC token validation.
     *
     * @param provider  e.g. "google", "azure", "okta"
     * @param claims    decoded ID-token claims
     * @return a signed app JWT for the provisioned user
     */
    @Transactional
    public String provisionAndIssueToken(String provider, Map<String, Object> claims) {
        String sub         = getString(claims, "sub");
        String email       = getString(claims, "email");
        String displayName = getString(claims, "name");
        String avatarUrl   = getString(claims, "picture");

        UUID tenantId = TenantContext.get();

        User user = userRepository.findByOidcSubAndOidcProvider(sub, provider)
            .orElseGet(() -> {
                // derive a unique username from email prefix
                String base     = email != null ? email.split("@")[0] : sub;
                String username = uniqueUsername(base);
                log.info("JIT provisioning OIDC user sub={} provider={} tenant={}", sub, provider, tenantId);
                return userRepository.save(User.builder()
                    .username(username)
                    .email(email != null ? email : sub + "@" + provider + ".oidc")
                    .passwordHash("")          // no password for SSO users
                    .role(User.Role.USER)
                    .tenantId(tenantId)
                    .oidcSub(sub)
                    .oidcProvider(provider)
                    .displayName(displayName)
                    .avatarUrl(avatarUrl)
                    .build());
            });

        // This login's tenant becomes the active one — a new USER membership if this identity
        // hasn't been seen in this tenant before (ensureMembership is idempotent so this is a
        // no-op on repeat logins), otherwise whatever role they actually hold there today (an
        // admin who was promoted after their first JIT login must not be silently reset to USER).
        membershipService.ensureMembership(user.getId(), tenantId, User.Role.USER);
        User.Role activeRole = membershipRepository.findByUserIdAndTenantId(user.getId(), tenantId)
            .map(m -> m.getRole())
            .orElse(User.Role.USER);
        user.setTenantId(tenantId);
        user.setRole(activeRole);

        // Refresh mutable fields on every login
        if (displayName != null) user.setDisplayName(displayName);
        if (avatarUrl   != null) user.setAvatarUrl(avatarUrl);
        userRepository.save(user);

        return jwtService.generateToken(user);
    }

    private String getString(Map<String, Object> claims, String key) {
        Object v = claims.get(key);
        return v != null ? v.toString() : null;
    }

    private String uniqueUsername(String base) {
        String candidate = base;
        int suffix = 1;
        while (userRepository.existsByUsername(candidate)) {
            candidate = base + suffix++;
        }
        return candidate;
    }
}

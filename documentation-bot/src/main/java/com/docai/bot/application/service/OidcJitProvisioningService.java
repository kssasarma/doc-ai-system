package com.docai.bot.application.service;

import java.util.Map;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.docai.bot.config.TenantContext;
import com.docai.bot.domain.entity.User;
import com.docai.bot.domain.repository.UserRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Just-in-Time (JIT) provisioning: creates a local User row the first time an OIDC user logs in.
 * Subsequent logins update the display name / avatar from the IdP claims.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OidcJitProvisioningService {

    private final UserRepository userRepository;
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

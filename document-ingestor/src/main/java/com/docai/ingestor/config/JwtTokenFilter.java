package com.docai.ingestor.config;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import javax.crypto.SecretKey;

import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@RequiredArgsConstructor
public class JwtTokenFilter extends OncePerRequestFilter {

    /** The exact value application.yml's dev default decodes to — same known secret the bot
     * service guards against; a real deploy signing/verifying tokens with this is forgeable. */
    private static final String DEV_DEFAULT_SECRET = "bXktc2VjcmV0LWtleS1mb3ItZG9jYWktc3lzdGVtLWp3dC10b2tlbnMtMjAyNA==";

    private final Environment environment;

    @Value("${app.jwt.secret}")
    private String secret;

    @PostConstruct
    void validateSecretIsNotDevDefault() {
        if (!DEV_DEFAULT_SECRET.equals(secret)) return;
        boolean isDevLikeProfile = Arrays.stream(environment.getActiveProfiles())
            .anyMatch(p -> p.equalsIgnoreCase("dev") || p.equalsIgnoreCase("local") || p.equalsIgnoreCase("test"));
        if (!isDevLikeProfile) {
            throw new IllegalStateException(
                "Refusing to start: JWT_SECRET is unset, so this service would verify tokens "
                + "against the publicly-known development default from this repo. Set JWT_SECRET "
                + "to the same real secret configured on documentation-bot, or explicitly activate "
                + "a dev/local/test Spring profile if this is intentionally a local run.");
        }
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {
        try {
            String token = extractToken(request);

            if (token != null) {
                try {
                    Claims claims = Jwts.parser()
                        .verifyWith(getSigningKey())
                        .build()
                        .parseSignedClaims(token)
                        .getPayload();

                    String role = claims.get("role", String.class);
                    String username = claims.get("username", String.class);
                    UUID userId = UUID.fromString(claims.getSubject());
                    String tenantClaim = claims.get("tenantId", String.class);
                    UUID tenantId = tenantClaim != null ? UUID.fromString(tenantClaim) : null;

                    if (tenantId != null) {
                        TenantContext.set(tenantId);
                        MDC.put("tenantId", tenantId.toString());
                    }
                    MDC.put("userId", userId.toString());

                    List<SimpleGrantedAuthority> authorities = List.of(
                        new SimpleGrantedAuthority("ROLE_" + role)
                    );

                    AdminPrincipal principal = new AdminPrincipal(userId, username, role, tenantId);
                    UsernamePasswordAuthenticationToken auth =
                        new UsernamePasswordAuthenticationToken(principal, null, authorities);
                    auth.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));

                    SecurityContextHolder.getContext().setAuthentication(auth);
                } catch (JwtException | IllegalArgumentException e) {
                    log.warn("Invalid JWT token: {}", e.getMessage());
                }
            }

            filterChain.doFilter(request, response);
        } finally {
            TenantContext.clear();
            MDC.remove("tenantId");
            MDC.remove("userId");
        }
    }

    private String extractToken(HttpServletRequest request) {
        String bearer = request.getHeader("Authorization");
        if (StringUtils.hasText(bearer) && bearer.startsWith("Bearer ")) {
            return bearer.substring(7);
        }
        return null;
    }

    private SecretKey getSigningKey() {
        return Keys.hmacShaKeyFor(Decoders.BASE64.decode(secret));
    }

    public record AdminPrincipal(UUID userId, String username, String role, UUID tenantId) {}
}

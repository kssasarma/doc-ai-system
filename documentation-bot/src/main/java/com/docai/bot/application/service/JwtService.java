package com.docai.bot.application.service;

import java.util.Arrays;
import java.util.Date;
import java.util.UUID;

import javax.crypto.SecretKey;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;

import com.docai.bot.domain.entity.User;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class JwtService {

    /** The exact value application.yml's dev default decodes to — a real deploy signing tokens
     * with this is a forgeable-JWT vulnerability, not a "works for now" shortcut. */
    private static final String DEV_DEFAULT_SECRET = "bXktc2VjcmV0LWtleS1mb3ItZG9jYWktc3lzdGVtLWp3dC10b2tlbnMtMjAyNA==";

    private final Environment environment;

    @Value("${app.jwt.secret}")
    private String secret;

    @Value("${app.jwt.expiration-ms:86400000}")
    private long expirationMs;

    /**
     * `application-prod.yml` already requires JWT_SECRET with no default, so a deploy that
     * correctly sets `SPRING_PROFILES_ACTIVE=prod` fails to boot on its own if the env var is
     * missing. This closes the actual gap: a deploy that forgets to set a profile at all (the
     * default profile, which is what a forgotten `SPRING_PROFILES_ACTIVE` falls back to) would
     * otherwise silently sign every token with the public dev secret checked into this repo.
     */
    @PostConstruct
    void validateSecretIsNotDevDefault() {
        if (!DEV_DEFAULT_SECRET.equals(secret)) return;
        boolean isDevLikeProfile = Arrays.stream(environment.getActiveProfiles())
            .anyMatch(p -> p.equalsIgnoreCase("dev") || p.equalsIgnoreCase("local") || p.equalsIgnoreCase("test"));
        if (!isDevLikeProfile) {
            throw new IllegalStateException(
                "Refusing to start: JWT_SECRET is unset, so the app would sign tokens with the "
                + "publicly-known development default from this repo. Set JWT_SECRET to a real "
                + "secret (openssl rand -base64 64), or explicitly activate a dev/local/test "
                + "Spring profile if this is intentionally a local run.");
        }
    }

    public String generateToken(User user) {
        return Jwts.builder()
            .subject(user.getId().toString())
            .claim("username", user.getUsername())
            .claim("role", user.getRole().name())
            .claim("tenantId", user.getTenantId() != null ? user.getTenantId().toString() : null)
            .claim("mustChangePassword", user.isMustChangePassword())
            .issuedAt(new Date())
            .expiration(new Date(System.currentTimeMillis() + expirationMs))
            .signWith(getSigningKey())
            .compact();
    }

    public Claims extractAllClaims(String token) {
        return Jwts.parser()
            .verifyWith(getSigningKey())
            .build()
            .parseSignedClaims(token)
            .getPayload();
    }

    public UUID extractUserId(String token) {
        return UUID.fromString(extractAllClaims(token).getSubject());
    }

    public String extractRole(String token) {
        return extractAllClaims(token).get("role", String.class);
    }

    public String extractUsername(String token) {
        return extractAllClaims(token).get("username", String.class);
    }

    /** Null for SUPER_ADMIN, whose accounts are not scoped to any tenant. */
    public UUID extractTenantId(String token) {
        String claim = extractAllClaims(token).get("tenantId", String.class);
        return claim != null ? UUID.fromString(claim) : null;
    }

    public boolean extractMustChangePassword(String token) {
        Boolean claim = extractAllClaims(token).get("mustChangePassword", Boolean.class);
        return Boolean.TRUE.equals(claim);
    }

    public boolean isTokenValid(String token) {
        try {
            extractAllClaims(token);
            return true;
        } catch (JwtException | IllegalArgumentException e) {
            log.warn("Invalid JWT: {}", e.getMessage());
            return false;
        }
    }

    private SecretKey getSigningKey() {
        byte[] keyBytes = Decoders.BASE64.decode(secret);
        return Keys.hmacShaKeyFor(keyBytes);
    }
}

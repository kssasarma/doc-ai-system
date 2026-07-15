package com.docai.bot.config;

import java.util.Arrays;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.expression.method.DefaultMethodSecurityExpressionHandler;
import org.springframework.security.access.hierarchicalroles.RoleHierarchy;
import org.springframework.security.access.hierarchicalroles.RoleHierarchyImpl;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import lombok.RequiredArgsConstructor;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthFilter jwtAuthFilter;
    private final ApiKeyAuthFilter apiKeyAuthFilter;
    private final TenantResolutionFilter tenantResolutionFilter;
    private final RequestCorrelationFilter requestCorrelationFilter;

    @Value("${app.cors.allowed-origins:http://localhost:5173,http://localhost:3000}")
    private String allowedOrigins;

    private static final String[] PUBLIC_PATHS = {
        // Only the auth endpoints a caller with no session yet can genuinely reach — NOT the
        // whole /api/auth/** tree, which also holds authenticated-only endpoints (/me,
        // /change-password, /my-tenants, /switch-tenant, /logout) that a blanket wildcard here
        // would incorrectly leave open to anonymous callers.
        "/api/auth/login",
        "/api/auth/refresh",
        "/api/auth/accept-invite",
        "/api/auth/forgot-password",
        "/api/auth/reset-password",
        "/api/auth/oidc/**",  // Phase 7 — OIDC callback + config
        "/api/share/{token}", // GET only — must work for anonymous visitors of a public link.
                               // Deliberately NOT "/api/share/**": that would also cover
                               // POST /api/share/{token}/fork, which must stay authenticated.
        "/api/v1/**",
        // FAQ browsing requires authentication (see FaqController) — the approved-entries
        // surface is generated from one tenant's documents and query history, so it's
        // tenant-scoped content, not public content, even though it's read-only.
        "/api/branding",      // Phase 7 — white-label branding
        "/actuator/health",
        "/actuator/info",
        // /actuator/prometheus deliberately NOT listed here — an unauthenticated scrape target
        // leaks internal metrics/topology. Requires the normal ADMIN JWT like any other admin
        // surface; a real deployment scrapes it with a service credential or puts it on a
        // separate, network-isolated management port instead of exposing it anonymously.
        "/v3/api-docs/**",
        "/swagger-ui/**",
        "/swagger-ui.html"
    };

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        return http
            .csrf(csrf -> csrf.disable())
            .cors(cors -> cors.configurationSource(corsConfigurationSource()))
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .exceptionHandling(ex -> ex
                .authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED))
            )
            .authorizeHttpRequests(auth -> auth
                .requestMatchers(PUBLIC_PATHS).permitAll()
                .anyRequest().authenticated()
            )
            .addFilterBefore(apiKeyAuthFilter, UsernamePasswordAuthenticationFilter.class)
            .addFilterBefore(jwtAuthFilter, ApiKeyAuthFilter.class)
            // Registered relative to jwtAuthFilter's own (already-established) position rather
            // than a shared reference class, so it's unambiguously first — every other filter's
            // log lines, including the auth filters', get the correlation id in MDC.
            .addFilterBefore(requestCorrelationFilter, JwtAuthFilter.class)
            // Runs AFTER both auth filters so it can trust the already-authenticated principal's
            // own tenantId over any client-supplied header — see TenantResolutionFilter for why
            // that order is load-bearing, not incidental.
            .addFilterAfter(tenantResolutionFilter, ApiKeyAuthFilter.class)
            .build();
    }

    /** SUPER_ADMIN > ADMIN > USER. Without this, every {@code @PreAuthorize("hasRole('ADMIN')")}
     * (and {@code hasAnyRole}-without-SUPER_ADMIN) check across the admin controllers locks
     * SUPER_ADMIN out — a functional bug, not an intentional restriction: SUPER_ADMIN is a
     * superset role (it can also create tenants) and should pass any admin-level check. Declared
     * as a static bean method per Spring Security's guidance for beans consumed by
     * method-security infrastructure, which initializes very early. */
    @Bean
    static RoleHierarchy roleHierarchy() {
        return RoleHierarchyImpl.withDefaultRolePrefix()
            .role("SUPER_ADMIN").implies("ADMIN")
            .role("ADMIN").implies("USER")
            .build();
    }

    @Bean
    static DefaultMethodSecurityExpressionHandler methodSecurityExpressionHandler(RoleHierarchy roleHierarchy) {
        DefaultMethodSecurityExpressionHandler handler = new DefaultMethodSecurityExpressionHandler();
        handler.setRoleHierarchy(roleHierarchy);
        return handler;
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        List<String> origins = Arrays.asList(allowedOrigins.split(","));
        config.setAllowedOrigins(origins.stream().map(String::trim).toList());
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("*"));
        config.setAllowCredentials(true);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }
}

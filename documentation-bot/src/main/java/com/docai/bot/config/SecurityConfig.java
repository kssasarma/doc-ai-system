package com.docai.bot.config;

import java.util.Arrays;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
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
        "/api/auth/**",
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
        "/actuator/prometheus",
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

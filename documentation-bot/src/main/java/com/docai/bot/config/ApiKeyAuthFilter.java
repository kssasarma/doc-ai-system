package com.docai.bot.config;

import java.io.IOException;
import java.util.List;

import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import com.docai.bot.application.service.ApiKeyService;
import com.docai.bot.domain.entity.ApiKey;
import com.docai.bot.domain.entity.User;
import com.docai.bot.domain.repository.UserRepository;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@RequiredArgsConstructor
public class ApiKeyAuthFilter extends OncePerRequestFilter {

    private static final String API_KEY_HEADER = "X-API-Key";
    private static final String API_KEY_BEARER_PREFIX = "ApiKey ";

    private final ApiKeyService apiKeyService;
    private final UserRepository userRepository;

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        // Only apply to /api/v1/** paths
        return !request.getRequestURI().startsWith("/api/v1/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {

        String rawKey = extractKey(request);
        if (rawKey == null) {
            filterChain.doFilter(request, response);
            return;
        }

        try {
            apiKeyService.validateKey(rawKey).ifPresent(apiKey -> {
                userRepository.findById(apiKey.getUserId()).ifPresent(user -> {
                    setPrincipal(request, apiKey, user);
                    apiKeyService.touchLastUsed(apiKey.getId());
                });
            });
        } catch (Exception e) {
            log.warn("API key validation error: {}", e.getMessage());
        }

        filterChain.doFilter(request, response);
    }

    private void setPrincipal(HttpServletRequest request, ApiKey apiKey, User user) {
        UserPrincipal principal = new UserPrincipal(user.getId(), user.getUsername(), user.getRole().name());
        List<SimpleGrantedAuthority> authorities = List.of(new SimpleGrantedAuthority("ROLE_" + user.getRole().name()));

        UsernamePasswordAuthenticationToken auth =
            new UsernamePasswordAuthenticationToken(principal, apiKey, authorities);
        SecurityContextHolder.getContext().setAuthentication(auth);
    }

    private String extractKey(HttpServletRequest request) {
        // Support X-API-Key header
        String header = request.getHeader(API_KEY_HEADER);
        if (header != null && !header.isBlank()) return header.trim();

        // Support Authorization: ApiKey sk-docai-xxx
        String auth = request.getHeader("Authorization");
        if (auth != null && auth.startsWith(API_KEY_BEARER_PREFIX)) {
            return auth.substring(API_KEY_BEARER_PREFIX.length()).trim();
        }
        return null;
    }
}

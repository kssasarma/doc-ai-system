package com.docai.bot.adapter.rest;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.docai.bot.application.service.ApiKeyService;
import com.docai.bot.application.service.ApiKeyService.ApiKeyDTO;
import com.docai.bot.application.service.ApiKeyService.CreateKeyResult;
import com.docai.bot.application.service.AuditLogService;
import com.docai.bot.application.service.JwtService;
import com.docai.bot.config.GlobalExceptionHandler;
import com.docai.bot.config.SecurityConfig;
import com.docai.bot.config.UserPrincipal;
import com.docai.bot.domain.repository.TenantRepository;
import com.docai.bot.domain.repository.UserRepository;

@WebMvcTest(ApiKeyController.class)
@Import({SecurityConfig.class, GlobalExceptionHandler.class})
class ApiKeyControllerTest {

    @Autowired MockMvc mockMvc;

    @MockitoBean ApiKeyService apiKeyService;
    @MockitoBean AuditLogService auditLogService;

    // Filter dependencies
    @MockitoBean JwtService jwtService;
    @MockitoBean UserRepository userRepository;
    @MockitoBean TenantRepository tenantRepository;

    private static final UUID USER_ID   = UUID.randomUUID();
    private static final UUID TENANT_ID = UUID.randomUUID();
    private static final UUID KEY_ID    = UUID.randomUUID();

    // ── GET /api/keys ─────────────────────────────────────────────────────────

    @Test
    void listKeys_authenticated_returns200WithList() throws Exception {
        ApiKeyDTO dto = ApiKeyDTO.builder()
            .id(KEY_ID.toString())
            .userId(USER_ID.toString())
            .keyPrefix("sk-docai-te")
            .name("My API Key")
            .scopes(List.of("query"))
            .rateLimitPerMin(60)
            .revoked(false)
            .build();
        when(apiKeyService.listKeys(USER_ID)).thenReturn(List.of(dto));

        mockMvc.perform(get("/api/keys")
                .with(authentication(userAuth("alice", "USER"))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].name").value("My API Key"))
            .andExpect(jsonPath("$[0].keyPrefix").value("sk-docai-te"));
    }

    @Test
    void listKeys_unauthenticated_returns401() throws Exception {
        mockMvc.perform(get("/api/keys"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void listKeys_emptyList_returns200() throws Exception {
        when(apiKeyService.listKeys(USER_ID)).thenReturn(List.of());

        mockMvc.perform(get("/api/keys")
                .with(authentication(userAuth("alice", "USER"))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$").isEmpty());
    }

    // ── POST /api/keys ────────────────────────────────────────────────────────

    @Test
    void createKey_validRequest_returns200WithRawKey() throws Exception {
        ApiKeyDTO dto = ApiKeyDTO.builder()
            .id(KEY_ID.toString())
            .userId(USER_ID.toString())
            .keyPrefix("sk-docai-te")
            .name("CI Pipeline")
            .scopes(List.of("query"))
            .rateLimitPerMin(60)
            .revoked(false)
            .build();
        CreateKeyResult result = new CreateKeyResult(dto, "sk-docai-rawkeyvalue123");
        when(apiKeyService.createKey(eq(USER_ID), eq("CI Pipeline"), any(), any(), any()))
            .thenReturn(result);
        doNothing().when(auditLogService).log(any(), any(), any(), any(), any(), any(), any());

        mockMvc.perform(post("/api/keys")
                .with(authentication(userAuth("alice", "USER")))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"CI Pipeline\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.rawKey").value("sk-docai-rawkeyvalue123"))
            .andExpect(jsonPath("$.key.name").value("CI Pipeline"));
    }

    @Test
    void createKey_missingName_returns400() throws Exception {
        mockMvc.perform(post("/api/keys")
                .with(authentication(userAuth("alice", "USER")))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"\"}"))
            .andExpect(status().isBadRequest());
    }

    @Test
    void createKey_unauthenticated_returns401() throws Exception {
        mockMvc.perform(post("/api/keys")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Key\"}"))
            .andExpect(status().isUnauthorized());
    }

    // ── DELETE /api/keys/{id} ─────────────────────────────────────────────────

    @Test
    void revokeKey_ownKey_returns204() throws Exception {
        doNothing().when(apiKeyService).revokeKey(KEY_ID, USER_ID);
        doNothing().when(auditLogService).log(any(), any(), any(), any(), any(), any(), any());

        mockMvc.perform(delete("/api/keys/{id}", KEY_ID)
                .with(authentication(userAuth("alice", "USER"))))
            .andExpect(status().isNoContent());
    }

    @Test
    void revokeKey_unauthenticated_returns401() throws Exception {
        mockMvc.perform(delete("/api/keys/{id}", KEY_ID))
            .andExpect(status().isUnauthorized());
    }

    // ── DELETE /api/keys/admin/{id} ───────────────────────────────────────────

    @Test
    void revokeKeyAsAdmin_asAdmin_returns204() throws Exception {
        doNothing().when(apiKeyService).revokeKeyAsAdmin(KEY_ID);
        doNothing().when(auditLogService).log(any(), any(), any(), any(), any(), any(), any());

        mockMvc.perform(delete("/api/keys/admin/{id}", KEY_ID)
                .with(authentication(userAuth("alice", "ADMIN"))))
            .andExpect(status().isNoContent());
    }

    @Test
    void revokeKeyAsAdmin_asUser_returns403() throws Exception {
        mockMvc.perform(delete("/api/keys/admin/{id}", KEY_ID)
                .with(authentication(userAuth("bob", "USER"))))
            .andExpect(status().isForbidden());
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private static UsernamePasswordAuthenticationToken userAuth(String username, String role) {
        UserPrincipal principal = new UserPrincipal(USER_ID, username, role, TENANT_ID, false);
        return new UsernamePasswordAuthenticationToken(
            principal, null, List.of(new SimpleGrantedAuthority("ROLE_" + role)));
    }
}

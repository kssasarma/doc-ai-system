package com.docai.bot.adapter.rest;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.Optional;
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

import com.docai.bot.application.service.AccountLockedException;
import com.docai.bot.application.service.AuditLogService;
import com.docai.bot.application.service.ApiKeyService;
import com.docai.bot.application.service.InvitationService;
import com.docai.bot.application.service.JwtService;
import com.docai.bot.application.service.PasswordResetService;
import com.docai.bot.application.service.RefreshTokenService;
import com.docai.bot.application.service.RefreshTokenService.RotationResult;
import com.docai.bot.application.service.TenantMembershipService;
import com.docai.bot.application.service.TenantMembershipService.MembershipDTO;
import com.docai.bot.application.service.UserService;
import com.docai.bot.config.GlobalExceptionHandler;
import com.docai.bot.config.SecurityConfig;
import com.docai.bot.config.UserPrincipal;
import com.docai.bot.domain.entity.User;
import com.docai.bot.domain.repository.TenantRepository;
import com.docai.bot.domain.repository.UserRepository;

@WebMvcTest(AuthController.class)
@Import({SecurityConfig.class, GlobalExceptionHandler.class})
class AuthControllerTest {

    @Autowired MockMvc mockMvc;

    // Controller dependencies
    @MockitoBean UserService userService;
    @MockitoBean InvitationService invitationService;
    @MockitoBean TenantMembershipService tenantMembershipService;
    @MockitoBean JwtService jwtService;
    @MockitoBean RefreshTokenService refreshTokenService;
    @MockitoBean UserRepository userRepository;
    @MockitoBean AuditLogService auditLogService;
    @MockitoBean PasswordResetService passwordResetService;

    // Filter dependencies
    @MockitoBean TenantRepository tenantRepository;
    @MockitoBean ApiKeyService apiKeyService;

    private static final UUID USER_ID   = UUID.randomUUID();
    private static final UUID TENANT_ID = UUID.randomUUID();

    // ── POST /api/auth/login ───────────────────────────────────────────────────

    @Test
    void login_validCredentials_returns200WithToken() throws Exception {
        User user = activeUser();
        when(userService.authenticate("alice", "secret-pass-123")).thenReturn(user);
        when(jwtService.generateToken(user)).thenReturn("jwt-token");
        when(refreshTokenService.issue(USER_ID)).thenReturn("refresh-token");
        doNothing().when(auditLogService).log(any(), any(), any(), any(), any(), any(), any());

        mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"alice\",\"password\":\"secret-pass-123\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.token").value("jwt-token"))
            .andExpect(jsonPath("$.refreshToken").value("refresh-token"))
            .andExpect(jsonPath("$.username").value("alice"))
            .andExpect(jsonPath("$.role").value("ADMIN"));
    }

    @Test
    void login_invalidCredentials_returns401() throws Exception {
        when(userService.authenticate(any(), any()))
            .thenThrow(new IllegalArgumentException("Invalid credentials"));
        doNothing().when(auditLogService).log(any(), any(), any(), any(), any(), any(), any());

        mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"alice\",\"password\":\"wrong\"}"))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.error").value("Invalid credentials"));
    }

    @Test
    void login_lockedAccount_returns423() throws Exception {
        when(userService.authenticate(any(), any()))
            .thenThrow(new AccountLockedException("Too many failed login attempts. Try again in 15 minute(s)."));
        doNothing().when(auditLogService).log(any(), any(), any(), any(), any(), any(), any());

        mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"alice\",\"password\":\"wrong\"}"))
            .andExpect(status().isLocked())
            .andExpect(jsonPath("$.error").exists());
    }

    @Test
    void login_missingUsername_returns400() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"\",\"password\":\"pass\"}"))
            .andExpect(status().isBadRequest());
    }

    @Test
    void login_missingPassword_returns400() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"alice\",\"password\":\"\"}"))
            .andExpect(status().isBadRequest());
    }

    // ── POST /api/auth/refresh ─────────────────────────────────────────────────

    @Test
    void refresh_validToken_returns200WithNewTokens() throws Exception {
        User user = activeUser();
        RotationResult rotation = new RotationResult(user, "new-refresh-token");
        when(refreshTokenService.rotate("old-refresh-token")).thenReturn(rotation);
        when(jwtService.generateToken(user)).thenReturn("new-jwt-token");

        mockMvc.perform(post("/api/auth/refresh")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"refreshToken\":\"old-refresh-token\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.token").value("new-jwt-token"))
            .andExpect(jsonPath("$.refreshToken").value("new-refresh-token"));
    }

    @Test
    void refresh_invalidToken_returns401() throws Exception {
        when(refreshTokenService.rotate(any()))
            .thenThrow(new IllegalArgumentException("Token not found or expired"));

        mockMvc.perform(post("/api/auth/refresh")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"refreshToken\":\"bogus-token\"}"))
            .andExpect(status().isUnauthorized());
    }

    // ── POST /api/auth/logout ──────────────────────────────────────────────────

    @Test
    void logout_withToken_returns204() throws Exception {
        doNothing().when(refreshTokenService).revoke(any());

        mockMvc.perform(post("/api/auth/logout")
                .with(authentication(userAuth("alice", "USER")))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"refreshToken\":\"some-token\"}"))
            .andExpect(status().isNoContent());
    }

    @Test
    void logout_withoutBody_returns204() throws Exception {
        mockMvc.perform(post("/api/auth/logout")
                .with(authentication(userAuth("alice", "USER"))))
            .andExpect(status().isNoContent());
    }

    @Test
    void logout_unauthenticated_returns401() throws Exception {
        mockMvc.perform(post("/api/auth/logout")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"refreshToken\":\"token\"}"))
            .andExpect(status().isUnauthorized());
    }

    // ── GET /api/auth/me ───────────────────────────────────────────────────────

    @Test
    void me_authenticated_returns200WithProfile() throws Exception {
        User user = activeUser();
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));

        mockMvc.perform(get("/api/auth/me")
                .with(authentication(userAuth("alice", "ADMIN"))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.username").value("alice"))
            .andExpect(jsonPath("$.role").value("ADMIN"));
    }

    @Test
    void me_unauthenticated_returns401() throws Exception {
        mockMvc.perform(get("/api/auth/me"))
            .andExpect(status().isUnauthorized());
    }

    // ── POST /api/auth/forgot-password ────────────────────────────────────────

    @Test
    void forgotPassword_alwaysReturns200() throws Exception {
        doNothing().when(passwordResetService).requestReset(any());

        mockMvc.perform(post("/api/auth/forgot-password")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"alice@example.com\"}"))
            .andExpect(status().isOk());
    }

    @Test
    void forgotPassword_unknownEmail_stillReturns200() throws Exception {
        doNothing().when(passwordResetService).requestReset(any());

        mockMvc.perform(post("/api/auth/forgot-password")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"nobody@example.com\"}"))
            .andExpect(status().isOk());
    }

    @Test
    void forgotPassword_invalidEmail_returns400() throws Exception {
        mockMvc.perform(post("/api/auth/forgot-password")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"not-an-email\"}"))
            .andExpect(status().isBadRequest());
    }

    // ── POST /api/auth/reset-password ─────────────────────────────────────────

    @Test
    void resetPassword_validToken_returns200() throws Exception {
        doNothing().when(passwordResetService).resetPassword(any(), any());

        mockMvc.perform(post("/api/auth/reset-password")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"token\":\"valid-token\",\"newPassword\":\"new-secure-password-1\"}"))
            .andExpect(status().isOk());
    }

    @Test
    void resetPassword_expiredToken_returns400() throws Exception {
        doThrow(new IllegalArgumentException("Token expired or not found"))
            .when(passwordResetService).resetPassword(any(), any());

        mockMvc.perform(post("/api/auth/reset-password")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"token\":\"expired-token\",\"newPassword\":\"new-secure-password-1\"}"))
            .andExpect(status().isBadRequest());
    }

    @Test
    void resetPassword_passwordTooShort_returns400() throws Exception {
        mockMvc.perform(post("/api/auth/reset-password")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"token\":\"valid-token\",\"newPassword\":\"short\"}"))
            .andExpect(status().isBadRequest());
    }

    // ── GET /api/auth/my-tenants ───────────────────────────────────────────────

    @Test
    void myTenants_authenticated_returnsList() throws Exception {
        MembershipDTO membership = new MembershipDTO(TENANT_ID.toString(), "Acme Corp", "ADMIN", "2024-01-01T00:00:00");
        when(tenantMembershipService.listMyTenants(USER_ID)).thenReturn(List.of(membership));

        mockMvc.perform(get("/api/auth/my-tenants")
                .with(authentication(userAuth("alice", "ADMIN"))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].tenantName").value("Acme Corp"))
            .andExpect(jsonPath("$[0].role").value("ADMIN"));
    }

    // ── POST /api/auth/accept-invite ──────────────────────────────────────────

    @Test
    void acceptInvite_validToken_returns201() throws Exception {
        User user = activeUser();
        when(invitationService.accept(eq("invite-token"), eq("newuser"), any())).thenReturn(user);
        when(jwtService.generateToken(user)).thenReturn("jwt-token");
        when(refreshTokenService.issue(USER_ID)).thenReturn("refresh-token");
        doNothing().when(auditLogService).log(any(), any(), any(), any(), any(), any(), any());

        mockMvc.perform(post("/api/auth/accept-invite")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"token\":\"invite-token\",\"username\":\"newuser\",\"password\":\"secure-pass-001\"}"))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.token").value("jwt-token"));
    }

    @Test
    void acceptInvite_shortPassword_returns400() throws Exception {
        mockMvc.perform(post("/api/auth/accept-invite")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"token\":\"token\",\"username\":\"user\",\"password\":\"short\"}"))
            .andExpect(status().isBadRequest());
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private static UsernamePasswordAuthenticationToken userAuth(String username, String role) {
        UserPrincipal principal = new UserPrincipal(USER_ID, username, role, TENANT_ID, false);
        return new UsernamePasswordAuthenticationToken(
            principal, null, List.of(new SimpleGrantedAuthority("ROLE_" + role)));
    }

    private static User activeUser() {
        return User.builder()
            .id(USER_ID)
            .username("alice")
            .email("alice@example.com")
            .passwordHash("hashed")
            .role(User.Role.ADMIN)
            .tenantId(TENANT_ID)
            .mustChangePassword(false)
            .build();
    }
}

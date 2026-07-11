package com.docai.bot.adapter.rest;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.docai.bot.application.service.InvitationService;
import com.docai.bot.application.service.JwtService;
import com.docai.bot.application.service.TenantMembershipService;
import com.docai.bot.application.service.TenantMembershipService.MembershipDTO;
import com.docai.bot.application.service.UserService;
import com.docai.bot.config.UserPrincipal;
import com.docai.bot.domain.entity.User;
import com.docai.bot.domain.repository.UserRepository;

import java.util.List;
import java.util.UUID;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Builder;
import lombok.Data;
import lombok.RequiredArgsConstructor;

/**
 * There is no public self-registration. The very first account on a fresh install is a fixed
 * SUPER_ADMIN seeded at startup (see {@link com.docai.bot.config.AdminSeeder}), which must change
 * its password on first login via {@code /change-password}; every other account is provisioned
 * via an invitation (see {@link com.docai.bot.adapter.rest.InvitationController}) and activated
 * here via {@code /accept-invite}.
 */
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final UserService userService;
    private final InvitationService invitationService;
    private final TenantMembershipService tenantMembershipService;
    private final JwtService jwtService;
    private final UserRepository userRepository;

    @PostMapping("/accept-invite")
    public ResponseEntity<AuthResponse> acceptInvite(@Valid @RequestBody AcceptInviteRequest request) {
        try {
            User user = invitationService.accept(request.getToken(), request.getUsername(), request.getPassword());
            String token = jwtService.generateToken(user);
            return ResponseEntity.status(HttpStatus.CREATED).body(AuthResponse.of(user, token));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(AuthResponse.error(e.getMessage()));
        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.GONE).body(AuthResponse.error(e.getMessage()));
        }
    }

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest request) {
        try {
            User user = userService.authenticate(request.getUsername(), request.getPassword());
            String token = jwtService.generateToken(user);
            return ResponseEntity.ok(AuthResponse.of(user, token));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(AuthResponse.error(e.getMessage()));
        }
    }

    @GetMapping("/me")
    public ResponseEntity<AuthResponse> me(@AuthenticationPrincipal UserPrincipal principal) {
        return userRepository.findById(principal.userId())
            .map(user -> ResponseEntity.ok(AuthResponse.of(user, null)))
            .orElse(ResponseEntity.notFound().build());
    }

    /** Verifies the current password and sets a new one, clearing any pending forced-reset flag. */
    @PostMapping("/change-password")
    public ResponseEntity<AuthResponse> changePassword(@Valid @RequestBody ChangePasswordRequest request,
                                                          @AuthenticationPrincipal UserPrincipal principal) {
        try {
            User user = userService.changePassword(principal.userId(), request.getCurrentPassword(), request.getNewPassword());
            String token = jwtService.generateToken(user);
            return ResponseEntity.ok(AuthResponse.of(user, token));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(AuthResponse.error(e.getMessage()));
        }
    }

    /** Every tenant the caller belongs to — backs a Slack-workspace-style tenant switcher. */
    @GetMapping("/my-tenants")
    public ResponseEntity<List<MembershipDTO>> myTenants(@AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(tenantMembershipService.listMyTenants(principal.userId()));
    }

    /** Flips the caller's active tenant to one they already hold a membership in and reissues a
     * token carrying the new tenant/role — the old token remains valid (but stale) until it expires. */
    @PostMapping("/switch-tenant/{tenantId}")
    public ResponseEntity<AuthResponse> switchTenant(@PathVariable UUID tenantId,
                                                       @AuthenticationPrincipal UserPrincipal principal) {
        try {
            User user = tenantMembershipService.switchActiveTenant(principal.userId(), tenantId);
            String token = jwtService.generateToken(user);
            return ResponseEntity.ok(AuthResponse.of(user, token));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(AuthResponse.error(e.getMessage()));
        }
    }

    @Data
    static class ChangePasswordRequest {
        @NotBlank
        private String currentPassword;
        @NotBlank
        @Size(min = 6)
        private String newPassword;
    }

    @Data
    static class LoginRequest {
        @NotBlank
        private String username;
        @NotBlank
        private String password;
    }

    @Data
    static class AcceptInviteRequest {
        @NotBlank
        private String token;
        @NotBlank
        @Size(min = 3, max = 50)
        private String username;
        @NotBlank
        @Size(min = 6)
        private String password;
    }

    @Data
    @Builder
    public static class AuthResponse {
        private String token;
        private String userId;
        private String username;
        private String email;
        private String role;
        private boolean mustChangePassword;
        private String error;

        public static AuthResponse of(User user, String token) {
            return AuthResponse.builder()
                .token(token)
                .userId(user.getId().toString())
                .username(user.getUsername())
                .email(user.getEmail())
                .role(user.getRole().name())
                .mustChangePassword(user.isMustChangePassword())
                .build();
        }

        public static AuthResponse error(String message) {
            return AuthResponse.builder().error(message).build();
        }
    }
}

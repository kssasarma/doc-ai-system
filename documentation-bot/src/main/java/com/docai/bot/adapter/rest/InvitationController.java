package com.docai.bot.adapter.rest;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.docai.bot.application.service.InvitationService;
import com.docai.bot.config.UserPrincipal;
import com.docai.bot.domain.entity.InvitationToken;
import com.docai.bot.domain.entity.User;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import lombok.RequiredArgsConstructor;

/**
 * SUPER_ADMIN invites a tenant's first ADMIN (must supply tenantId).
 * ADMIN invites a USER into their own tenant (tenantId is always the caller's own — never
 * trusted from the request body, so an admin can never provision a user into another tenant).
 */
@RestController
@RequestMapping("/api/admin/invitations")
@RequiredArgsConstructor
public class InvitationController {

    private final InvitationService invitationService;

    @PostMapping
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ADMIN')")
    public ResponseEntity<?> invite(@Valid @RequestBody InviteRequest request,
                                     @AuthenticationPrincipal UserPrincipal principal) {
        try {
            User.Role role;
            java.util.UUID tenantId;

            if (principal.isSuperAdmin()) {
                role = User.Role.ADMIN;
                tenantId = request.getTenantId();
                if (tenantId == null) {
                    return ResponseEntity.badRequest().body(errorBody("tenantId is required"));
                }
            } else {
                role = User.Role.USER;
                tenantId = principal.tenantId();
            }

            InvitationToken invitation = invitationService.invite(request.getEmail(), role, tenantId, principal.userId());
            return ResponseEntity.status(HttpStatus.CREATED).body(new InviteResponse(
                invitation.getId().toString(), invitation.getEmail(), invitation.getRole().name(),
                invitation.getExpiresAt().toString()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(errorBody(e.getMessage()));
        }
    }

    private static java.util.Map<String, String> errorBody(String message) {
        return java.util.Map.of("error", message);
    }

    @Data
    static class InviteRequest {
        @NotBlank
        @Email
        private String email;
        /** Only read for a SUPER_ADMIN caller — ignored (forced to the caller's own tenant) for an ADMIN caller. */
        private java.util.UUID tenantId;
    }

    record InviteResponse(String id, String email, String role, String expiresAt) {}
}

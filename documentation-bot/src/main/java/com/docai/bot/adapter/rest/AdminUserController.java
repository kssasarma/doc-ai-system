package com.docai.bot.adapter.rest;

import java.time.LocalDateTime;
import java.util.Objects;
import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.docai.bot.application.service.AuditLogService;
import com.docai.bot.application.service.GdprErasureService;
import com.docai.bot.config.UserPrincipal;
import com.docai.bot.domain.entity.User;
import com.docai.bot.domain.repository.UserRepository;

import lombok.Data;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/admin/users")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
public class AdminUserController {

    private final GdprErasureService erasureService;
    private final UserRepository userRepository;
    private final AuditLogService auditLogService;

    /** Processes a user's GDPR deletion request (or any admin-initiated erasure). Permanent —
     * see {@link #deactivate} for a reversible alternative. */
    @DeleteMapping("/{userId}")
    public ResponseEntity<Void> eraseUser(@PathVariable UUID userId,
                                           @AuthenticationPrincipal UserPrincipal principal) {
        User target = resolveInCallerTenant(userId, principal);
        erasureService.eraseUser(userId);
        auditLogService.log(principal.userId(), principal.tenantId(), "USER_ERASE", "USER", userId, null, null);
        return ResponseEntity.noContent().build();
    }

    /** Promotes/demotes between USER and ADMIN — SUPER_ADMIN is a platform-level role, never
     * assignable here. Refuses to demote a tenant's last remaining active ADMIN, which would
     * leave the tenant with nobody able to manage it. */
    @PatchMapping("/{userId}/role")
    public ResponseEntity<?> changeRole(@PathVariable UUID userId, @RequestBody ChangeRoleRequest request,
                                         @AuthenticationPrincipal UserPrincipal principal) {
        User target = resolveInCallerTenant(userId, principal);
        User.Role newRole;
        try {
            newRole = User.Role.valueOf(request.getRole());
        } catch (IllegalArgumentException | NullPointerException e) {
            return ResponseEntity.badRequest().body(java.util.Map.of("error", "role must be ADMIN or USER"));
        }
        if (newRole == User.Role.SUPER_ADMIN) {
            return ResponseEntity.badRequest().body(java.util.Map.of("error", "role must be ADMIN or USER"));
        }
        if (target.getRole() == User.Role.ADMIN && newRole != User.Role.ADMIN
                && userRepository.countByTenantIdAndRoleAndDeactivatedAtIsNull(principal.tenantId(), User.Role.ADMIN) <= 1) {
            return ResponseEntity.badRequest().body(
                java.util.Map.of("error", "Cannot demote the tenant's last remaining admin"));
        }
        target.setRole(newRole);
        userRepository.save(target);
        auditLogService.log(principal.userId(), principal.tenantId(), "USER_ROLE_CHANGE", "USER", userId,
            "role=" + newRole, null);
        return ResponseEntity.ok(target.getRole().name());
    }

    /** Reversible login block — see {@link #eraseUser} for the permanent GDPR alternative. Same
     * last-admin guard as role changes. */
    @PostMapping("/{userId}/deactivate")
    public ResponseEntity<?> deactivate(@PathVariable UUID userId, @AuthenticationPrincipal UserPrincipal principal) {
        User target = resolveInCallerTenant(userId, principal);
        if (target.getRole() == User.Role.ADMIN
                && userRepository.countByTenantIdAndRoleAndDeactivatedAtIsNull(principal.tenantId(), User.Role.ADMIN) <= 1) {
            return ResponseEntity.badRequest().body(
                java.util.Map.of("error", "Cannot deactivate the tenant's last remaining admin"));
        }
        target.setDeactivatedAt(LocalDateTime.now());
        userRepository.save(target);
        auditLogService.log(principal.userId(), principal.tenantId(), "USER_DEACTIVATE", "USER", userId, null, null);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{userId}/reactivate")
    public ResponseEntity<?> reactivate(@PathVariable UUID userId, @AuthenticationPrincipal UserPrincipal principal) {
        User target = resolveInCallerTenant(userId, principal);
        target.setDeactivatedAt(null);
        userRepository.save(target);
        auditLogService.log(principal.userId(), principal.tenantId(), "USER_REACTIVATE", "USER", userId, null, null);
        return ResponseEntity.noContent().build();
    }

    private User resolveInCallerTenant(UUID userId, UserPrincipal principal) {
        User target = userRepository.findById(userId)
            .orElseThrow(() -> new IllegalArgumentException("User not found"));
        if (!principal.isSuperAdmin() && !Objects.equals(target.getTenantId(), principal.tenantId())) {
            throw new AccessDeniedException("You do not have access to this user");
        }
        return target;
    }

    @Data
    static class ChangeRoleRequest {
        private String role;
    }
}

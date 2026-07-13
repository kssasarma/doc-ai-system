package com.docai.bot.adapter.rest;

import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.docai.bot.application.service.GdprErasureService;
import com.docai.bot.config.UserPrincipal;
import com.docai.bot.domain.entity.User;
import com.docai.bot.domain.repository.UserRepository;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/admin/users")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
public class AdminUserController {

    private final GdprErasureService erasureService;
    private final UserRepository userRepository;

    /** Processes a user's GDPR deletion request (or any admin-initiated erasure). */
    @DeleteMapping("/{userId}")
    public ResponseEntity<Void> eraseUser(@PathVariable UUID userId,
                                           @AuthenticationPrincipal UserPrincipal principal) {
        User target = userRepository.findById(userId)
            .orElseThrow(() -> new IllegalArgumentException("User not found"));
        if (!principal.isSuperAdmin() && !target.getTenantId().equals(principal.tenantId())) {
            throw new AccessDeniedException("You do not have access to this user");
        }
        erasureService.eraseUser(userId);
        return ResponseEntity.noContent().build();
    }
}

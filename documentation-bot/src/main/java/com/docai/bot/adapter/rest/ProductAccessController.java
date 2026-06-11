package com.docai.bot.adapter.rest;

import java.util.List;
import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.docai.bot.application.service.AuditLogService;
import com.docai.bot.application.service.ProductAccessService;
import com.docai.bot.config.UserPrincipal;

import lombok.Data;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/admin/product-access")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
public class ProductAccessController {

    private final ProductAccessService productAccessService;
    private final AuditLogService auditLogService;

    @GetMapping("/users")
    public ResponseEntity<List<ProductAccessService.UserWithAccessDTO>> getAllUsersWithAccess() {
        return ResponseEntity.ok(productAccessService.getAllUsersWithAccess());
    }

    @PostMapping
    public ResponseEntity<ProductAccessService.ProductAccessGrantDTO> grantAccess(
            @RequestBody GrantRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {
        ProductAccessService.ProductAccessGrantDTO grant = productAccessService.grantAccess(
            UUID.fromString(request.getUserId()),
            request.getProduct(),
            request.getVersion(),
            principal.userId()
        );
        auditLogService.log(principal.userId(), "PRODUCT_ACCESS_GRANT", "USER",
            UUID.fromString(request.getUserId()),
            request.getProduct() + " " + request.getVersion(), null);
        return ResponseEntity.ok(grant);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> revokeAccess(
            @PathVariable String id,
            @AuthenticationPrincipal UserPrincipal principal) {
        productAccessService.revokeAccess(UUID.fromString(id), principal.userId());
        auditLogService.log(principal.userId(), "PRODUCT_ACCESS_REVOKE", "ACCESS_GRANT",
            UUID.fromString(id), null, null);
        return ResponseEntity.noContent().build();
    }

    @Data
    public static class GrantRequest {
        private String userId;
        private String product;
        private String version;
    }
}

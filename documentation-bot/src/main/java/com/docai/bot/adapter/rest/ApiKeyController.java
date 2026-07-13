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

import com.docai.bot.application.service.ApiKeyService;
import com.docai.bot.application.service.ApiKeyService.ApiKeyDTO;
import com.docai.bot.application.service.ApiKeyService.CreateKeyResult;
import com.docai.bot.application.service.AuditLogService;
import com.docai.bot.config.UserPrincipal;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/keys")
@RequiredArgsConstructor
public class ApiKeyController {

    private final ApiKeyService apiKeyService;
    private final AuditLogService auditLogService;

    @GetMapping
    public ResponseEntity<List<ApiKeyDTO>> listMyKeys(@AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(apiKeyService.listKeys(principal.userId()));
    }

    @PostMapping
    public ResponseEntity<CreateKeyResponse> createKey(
            @Valid @RequestBody CreateKeyRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {

        CreateKeyResult result = apiKeyService.createKey(
            principal.userId(),
            request.getName(),
            request.getScopes() != null ? request.getScopes() : new String[]{"query"},
            request.getRateLimitPerMin(),
            request.getExpirationDays()
        );

        auditLogService.log(principal.userId(), principal.tenantId(), "API_KEY_CREATE", "API_KEY",
            UUID.fromString(result.dto().getId()), result.dto().getName(), null);

        return ResponseEntity.ok(new CreateKeyResponse(result.dto(), result.rawKey()));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> revokeKey(
            @PathVariable String id,
            @AuthenticationPrincipal UserPrincipal principal) {

        apiKeyService.revokeKey(UUID.fromString(id), principal.userId());
        auditLogService.log(principal.userId(), principal.tenantId(), "API_KEY_REVOKE", "API_KEY",
            UUID.fromString(id), null, null);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/admin/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> revokeKeyAsAdmin(
            @PathVariable String id,
            @AuthenticationPrincipal UserPrincipal principal) {

        apiKeyService.revokeKeyAsAdmin(UUID.fromString(id));
        auditLogService.log(principal.userId(), principal.tenantId(), "API_KEY_ADMIN_REVOKE", "API_KEY",
            UUID.fromString(id), null, null);
        return ResponseEntity.noContent().build();
    }

    @Data
    static class CreateKeyRequest {
        @NotBlank(message = "name is required")
        @Size(max = 100)
        private String name;
        private String[] scopes;
        private Integer rateLimitPerMin;
        private Integer expirationDays;
    }

    record CreateKeyResponse(ApiKeyDTO key, String rawKey) {}
}

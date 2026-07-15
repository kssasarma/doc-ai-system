package com.docai.bot.adapter.rest;

import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.docai.bot.application.service.AuditLogService;
import com.docai.bot.application.service.InvitationService;
import com.docai.bot.application.service.TenantService;
import com.docai.bot.config.UserPrincipal;
import com.docai.bot.domain.entity.DataRetentionPolicy;
import com.docai.bot.domain.entity.Tenant;
import com.docai.bot.domain.entity.TenantBranding;
import com.docai.bot.domain.entity.User;
import com.docai.bot.domain.repository.UserRepository;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;

/**
 * Tenant CRUD (list/create/update-plan) is SUPER_ADMIN-only — it's a platform-level operation.
 * Per-tenant config (branding/LLM/retention) is self-service: a tenant's own ADMIN may read/write
 * only their own tenant's config; SUPER_ADMIN may access any tenant's for support purposes.
 */
@RestController
@RequestMapping("/api/admin/tenants")
@RequiredArgsConstructor
public class TenantController {

    private final TenantService tenantService;
    private final InvitationService invitationService;
    private final UserRepository userRepository;
    private final AuditLogService auditLogService;

    @GetMapping
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public List<Tenant> listAll() {
        return tenantService.listAll();
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public Tenant getById(@PathVariable UUID id) {
        return tenantService.getById(id);
    }

    /** Creates the tenant, then emails its adminEmail an invite to become that tenant's first ADMIN. */
    @PostMapping
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public Tenant create(@RequestBody CreateTenantRequest req, @AuthenticationPrincipal UserPrincipal principal,
                          HttpServletRequest request) {
        Tenant tenant = tenantService.create(req.name(), req.slug(), req.plan(),
            req.maxUsers() > 0 ? req.maxUsers() : 10,
            req.maxDocuments() > 0 ? req.maxDocuments() : 100);
        invitationService.invite(req.adminEmail(), User.Role.ADMIN, tenant.getId(), principal.userId());
        auditLogService.log(principal.userId(), tenant.getId(), "TENANT_CREATE", "TENANT", tenant.getId(),
            "name=" + req.name() + ",slug=" + req.slug() + ",plan=" + req.plan(), request.getRemoteAddr());
        return tenant;
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public Tenant update(@PathVariable UUID id, @RequestBody UpdateTenantRequest req,
                          @AuthenticationPrincipal UserPrincipal principal, HttpServletRequest request) {
        Tenant updated = tenantService.update(id, req.name(), req.plan(), req.active(),
            req.maxUsers(), req.maxDocuments());
        auditLogService.log(principal.userId(), id, req.active() ? "TENANT_UPDATE" : "TENANT_DEACTIVATE", "TENANT", id,
            "name=" + req.name() + ",plan=" + req.plan() + ",active=" + req.active(), request.getRemoteAddr());
        return updated;
    }

    @GetMapping("/{id}/branding")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ADMIN')")
    public ResponseEntity<TenantBranding> getBranding(@PathVariable UUID id, @AuthenticationPrincipal UserPrincipal principal) {
        return ownTenantOrSuperAdmin(id, principal, () -> tenantService.getBranding(id));
    }

    @PutMapping("/{id}/branding")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ADMIN')")
    public ResponseEntity<TenantBranding> updateBranding(@PathVariable UUID id, @RequestBody TenantBranding body,
                                                          @AuthenticationPrincipal UserPrincipal principal,
                                                          HttpServletRequest request) {
        return ownTenantOrSuperAdmin(id, principal, () -> {
            TenantBranding result = tenantService.updateBranding(id, body);
            auditLogService.log(principal.userId(), id, "TENANT_BRANDING_UPDATE", "TENANT", id, null, request.getRemoteAddr());
            return result;
        });
    }

    @GetMapping("/{id}/llm-config")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ADMIN')")
    public ResponseEntity<TenantService.LlmConfigView> getLLMConfig(@PathVariable UUID id, @AuthenticationPrincipal UserPrincipal principal) {
        return ownTenantOrSuperAdmin(id, principal, () -> tenantService.getLLMConfig(id));
    }

    @PutMapping("/{id}/llm-config")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ADMIN')")
    public ResponseEntity<TenantService.LlmConfigView> updateLLMConfig(@PathVariable UUID id, @RequestBody TenantService.LlmConfigUpdate body,
                                                            @AuthenticationPrincipal UserPrincipal principal,
                                                            HttpServletRequest request) {
        return ownTenantOrSuperAdmin(id, principal, () -> {
            TenantService.LlmConfigView result = tenantService.updateLLMConfig(id, body);
            auditLogService.log(principal.userId(), id, "TENANT_LLM_CONFIG_UPDATE", "TENANT", id,
                "provider=" + body.chatProvider() + ",model=" + body.chatModel()
                    + ",customKeyChanged=" + (body.apiKey() != null), request.getRemoteAddr());
            return result;
        });
    }

    /** Tests a chat provider/model/key combination without persisting — the admin UI's "Test
     * connection" action. An empty/omitted apiKey re-tests whatever key is already stored. */
    @PostMapping("/{id}/llm-config/test")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ADMIN')")
    public ResponseEntity<TenantService.TestConnectionResult> testLLMConfig(@PathVariable UUID id,
                                                            @RequestBody TestLlmConfigRequest body,
                                                            @AuthenticationPrincipal UserPrincipal principal) {
        return ownTenantOrSuperAdmin(id, principal,
            () -> tenantService.testConnection(id, body.provider(), body.model(), body.apiKey()));
    }

    @GetMapping("/{id}/retention")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ADMIN')")
    public ResponseEntity<DataRetentionPolicy> getRetention(@PathVariable UUID id, @AuthenticationPrincipal UserPrincipal principal) {
        return ownTenantOrSuperAdmin(id, principal, () -> tenantService.getRetentionPolicy(id));
    }

    @PutMapping("/{id}/retention")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ADMIN')")
    public ResponseEntity<DataRetentionPolicy> updateRetention(@PathVariable UUID id, @RequestBody DataRetentionPolicy body,
                                                                @AuthenticationPrincipal UserPrincipal principal,
                                                                HttpServletRequest request) {
        return ownTenantOrSuperAdmin(id, principal, () -> {
            DataRetentionPolicy result = tenantService.updateRetentionPolicy(id, body);
            auditLogService.log(principal.userId(), id, "TENANT_RETENTION_UPDATE", "TENANT", id, null, request.getRemoteAddr());
            return result;
        });
    }

    /** Searchable, paginated user list for this tenant — backs the Users admin page and every
     * document/group grant picker (Combobox typeahead passes {@code q} on each keystroke). */
    @GetMapping("/{id}/users")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ADMIN')")
    public ResponseEntity<Page<TenantUserDTO>> getUsers(
            @PathVariable UUID id,
            @RequestParam(required = false) String q,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @AuthenticationPrincipal UserPrincipal principal) {
        Pageable pageable = PageRequest.of(page, size);
        return ownTenantOrSuperAdmin(id, principal, () -> userRepository.searchByTenantId(id, q, pageable)
            .map(u -> new TenantUserDTO(u.getId(), u.getUsername(), u.getEmail(), u.getRole().name(),
                u.getDeactivatedAt() == null)));
    }

    private <T> ResponseEntity<T> ownTenantOrSuperAdmin(UUID id, UserPrincipal principal, java.util.function.Supplier<T> action) {
        if (!principal.isSuperAdmin() && !id.equals(principal.tenantId())) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        return ResponseEntity.ok(action.get());
    }

    record TestLlmConfigRequest(String provider, String model, String apiKey) {}

    record CreateTenantRequest(String name, String slug, String plan, int maxUsers, int maxDocuments, String adminEmail) {}

    record UpdateTenantRequest(String name, String plan, boolean active, int maxUsers, int maxDocuments) {}

    record TenantUserDTO(UUID userId, String username, String email, String role, boolean active) {}
}

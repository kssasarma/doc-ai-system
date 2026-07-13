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
import com.docai.bot.application.service.GroupService;
import com.docai.bot.application.service.GroupService.GroupDTO;
import com.docai.bot.application.service.GroupService.MemberDTO;
import com.docai.bot.config.TenantContext;
import com.docai.bot.config.UserPrincipal;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.RequiredArgsConstructor;

/** Tenant-scoped, ADMIN-only group management — create groups and manage membership. Granting a
 * group access to a document is a separate concern, handled alongside per-user grants by
 * {@link DocumentAccessController} (see its {@code /groups} sub-paths). */
@RestController
@RequestMapping("/api/groups")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
public class GroupController {

    private final GroupService groupService;
    private final AuditLogService auditLogService;

    @GetMapping
    public ResponseEntity<List<GroupDTO>> list() {
        return ResponseEntity.ok(groupService.list(TenantContext.get()));
    }

    @PostMapping
    public ResponseEntity<GroupDTO> create(@Valid @RequestBody CreateGroupRequest request,
                                            @AuthenticationPrincipal UserPrincipal principal) {
        GroupDTO group = groupService.create(request.getName(), TenantContext.get(), principal.userId());
        auditLogService.log(principal.userId(), principal.tenantId(), "GROUP_CREATE", "GROUP",
            UUID.fromString(group.id()), "name=" + group.name(), null);
        return ResponseEntity.ok(group);
    }

    @DeleteMapping("/{groupId}")
    public ResponseEntity<Void> delete(@PathVariable UUID groupId,
                                        @AuthenticationPrincipal UserPrincipal principal) {
        groupService.delete(groupId, TenantContext.get());
        auditLogService.log(principal.userId(), principal.tenantId(), "GROUP_DELETE", "GROUP", groupId, null, null);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{groupId}/members")
    public ResponseEntity<List<MemberDTO>> listMembers(@PathVariable UUID groupId) {
        return ResponseEntity.ok(groupService.listMembers(groupId, TenantContext.get()));
    }

    @PostMapping("/{groupId}/members")
    public ResponseEntity<MemberDTO> addMember(@PathVariable UUID groupId,
                                                @Valid @RequestBody AddMemberRequest request,
                                                @AuthenticationPrincipal UserPrincipal principal) {
        MemberDTO member = groupService.addMember(groupId, request.getUserId(), TenantContext.get());
        auditLogService.log(principal.userId(), principal.tenantId(), "GROUP_MEMBER_ADD", "GROUP", groupId,
            "user=" + request.getUserId(), null);
        return ResponseEntity.ok(member);
    }

    @DeleteMapping("/{groupId}/members/{userId}")
    public ResponseEntity<Void> removeMember(@PathVariable UUID groupId,
                                              @PathVariable UUID userId,
                                              @AuthenticationPrincipal UserPrincipal principal) {
        groupService.removeMember(groupId, userId, TenantContext.get());
        auditLogService.log(principal.userId(), principal.tenantId(), "GROUP_MEMBER_REMOVE", "GROUP", groupId,
            "user=" + userId, null);
        return ResponseEntity.noContent().build();
    }

    @Data
    static class CreateGroupRequest {
        @NotBlank private String name;
    }

    @Data
    static class AddMemberRequest {
        @NotNull private UUID userId;
    }
}

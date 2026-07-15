package com.docai.bot.application.service;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.docai.bot.domain.entity.Group;
import com.docai.bot.domain.entity.GroupMembership;
import com.docai.bot.domain.entity.User;
import com.docai.bot.domain.repository.GroupMembershipRepository;
import com.docai.bot.domain.repository.GroupRepository;
import com.docai.bot.domain.repository.UserRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Manages tenant-scoped groups and their membership — the bulk-grant complement to
 * {@link DocumentAccessService}'s per-user grants. A group has no access of its own until an
 * admin grants it access to specific documents via {@link GroupDocumentAccessService}; this
 * service only owns "who's in the group," not "what the group can see."
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GroupService {

    private final GroupRepository groupRepository;
    private final GroupMembershipRepository membershipRepository;
    private final UserRepository userRepository;

    @Transactional
    public GroupDTO create(String name, UUID tenantId, UUID createdBy) {
        String trimmed = name == null ? "" : name.trim();
        if (trimmed.isBlank()) {
            throw new IllegalArgumentException("Group name is required");
        }
        if (groupRepository.existsByTenantIdAndName(tenantId, trimmed)) {
            throw new IllegalArgumentException("A group named '" + trimmed + "' already exists");
        }

        Group group = Group.builder()
            .tenantId(tenantId)
            .name(trimmed)
            .createdBy(createdBy)
            .build();
        // flush so the @CreationTimestamp-generated createdAt is populated in-memory before toDTO
        // reads it — Hibernate doesn't guarantee that until a flush (same reasoning as
        // DocumentAccessService.grant()).
        Group saved = groupRepository.saveAndFlush(group);
        log.info("Created group '{}' in tenant {}", trimmed, tenantId);
        return toDTO(saved, 0);
    }

    @Transactional
    public void delete(UUID groupId, UUID tenantId) {
        Group group = requireGroupInTenant(groupId, tenantId);
        // Memberships and any GroupDocumentAccess grants cascade via the DB FK (ON DELETE CASCADE).
        groupRepository.delete(group);
        log.info("Deleted group {} from tenant {}", groupId, tenantId);
    }

    @Transactional(readOnly = true)
    public List<GroupDTO> list(UUID tenantId, String q) {
        List<Group> groups = (q == null || q.isBlank())
            ? groupRepository.findByTenantId(tenantId)
            : groupRepository.findByTenantIdAndNameContainingIgnoreCase(tenantId, q.trim());
        return groups.stream()
            .map(g -> toDTO(g, membershipRepository.countByGroupId(g.getId())))
            .toList();
    }

    @Transactional
    public MemberDTO addMember(UUID groupId, UUID targetUserId, UUID tenantId) {
        requireGroupInTenant(groupId, tenantId);
        User targetUser = requireUserInTenant(targetUserId, tenantId);
        if (membershipRepository.existsByGroupIdAndUserId(groupId, targetUserId)) {
            throw new IllegalArgumentException("User is already a member of this group");
        }

        membershipRepository.save(GroupMembership.builder()
            .groupId(groupId)
            .userId(targetUserId)
            .build());
        log.info("Added user {} to group {}", targetUserId, groupId);
        return new MemberDTO(targetUser.getId().toString(), targetUser.getUsername(), targetUser.getEmail());
    }

    @Transactional
    public void removeMember(UUID groupId, UUID targetUserId, UUID tenantId) {
        requireGroupInTenant(groupId, tenantId);
        membershipRepository.deleteByGroupIdAndUserId(groupId, targetUserId);
        log.info("Removed user {} from group {}", targetUserId, groupId);
    }

    @Transactional(readOnly = true)
    public List<MemberDTO> listMembers(UUID groupId, UUID tenantId) {
        requireGroupInTenant(groupId, tenantId);

        List<GroupMembership> memberships = membershipRepository.findByGroupId(groupId);
        Map<UUID, User> users = userRepository.findAllById(
                memberships.stream().map(GroupMembership::getUserId).toList())
            .stream()
            .collect(Collectors.toMap(User::getId, u -> u));

        return memberships.stream()
            .map(m -> {
                User u = users.get(m.getUserId());
                return new MemberDTO(m.getUserId().toString(),
                    u != null ? u.getUsername() : "unknown",
                    u != null ? u.getEmail() : "");
            })
            .toList();
    }

    private Group requireGroupInTenant(UUID groupId, UUID tenantId) {
        return groupRepository.findByIdAndTenantId(groupId, tenantId)
            .orElseThrow(() -> new IllegalArgumentException("Group not found: " + groupId));
    }

    private User requireUserInTenant(UUID userId, UUID tenantId) {
        return userRepository.findByIdAndTenantId(userId, tenantId)
            .orElseThrow(() -> new IllegalArgumentException("User not found in this tenant: " + userId));
    }

    private GroupDTO toDTO(Group group, long memberCount) {
        return new GroupDTO(group.getId().toString(), group.getName(), memberCount, group.getCreatedAt().toString());
    }

    public record GroupDTO(String id, String name, long memberCount, String createdAt) {}

    public record MemberDTO(String userId, String username, String email) {}
}

package com.docai.bot.application.service;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.docai.bot.domain.entity.Document;
import com.docai.bot.domain.entity.Group;
import com.docai.bot.domain.entity.GroupDocumentAccess;
import com.docai.bot.domain.repository.DocumentRepository;
import com.docai.bot.domain.repository.GroupDocumentAccessRepository;
import com.docai.bot.domain.repository.GroupRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Manages which groups have access to which document — the bulk-grant sibling of
 * {@link DocumentAccessService}. A grant here gives every current (and future) member of the
 * group access; see {@code GrantBasedDocumentAccessPolicy} for where the two grant types are
 * unioned into a user's effective {@link com.docai.bot.domain.model.SearchScope} at retrieval time.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GroupDocumentAccessService {

    private final GroupDocumentAccessRepository groupDocumentAccessRepository;
    private final GroupRepository groupRepository;
    private final DocumentRepository documentRepository;

    @Transactional
    public GroupGranteeDTO grant(UUID documentId, UUID groupId, UUID tenantId, UUID grantedBy) {
        Document document = requireDocumentInTenant(documentId, tenantId);
        Group group = requireGroupInTenant(groupId, tenantId);

        GroupDocumentAccess grant = groupDocumentAccessRepository.findByDocumentIdAndGroupId(documentId, groupId)
            .orElseGet(() -> GroupDocumentAccess.builder()
                .documentId(document.getId())
                .groupId(group.getId())
                .tenantId(tenantId)
                .grantedBy(grantedBy)
                .build());

        // flush (not just save) so the @CreationTimestamp-generated grantedAt is populated
        // in-memory before it's read below — same reasoning as DocumentAccessService.grant().
        GroupDocumentAccess saved = groupDocumentAccessRepository.saveAndFlush(grant);
        log.info("Granted document {} access to group {} (tenant {})", documentId, groupId, tenantId);
        return toDTO(saved, group.getName());
    }

    @Transactional
    public void revoke(UUID documentId, UUID groupId, UUID tenantId) {
        requireDocumentInTenant(documentId, tenantId);
        groupDocumentAccessRepository.deleteByDocumentIdAndGroupId(documentId, groupId);
        log.info("Revoked document {} access from group {} (tenant {})", documentId, groupId, tenantId);
    }

    @Transactional(readOnly = true)
    public List<GroupGranteeDTO> listGrantedGroups(UUID documentId, UUID tenantId) {
        requireDocumentInTenant(documentId, tenantId);

        List<GroupDocumentAccess> grants = groupDocumentAccessRepository.findByDocumentIdAndTenantId(documentId, tenantId);
        Map<UUID, String> groupNames = groupRepository.findAllById(
                grants.stream().map(GroupDocumentAccess::getGroupId).toList())
            .stream()
            .collect(Collectors.toMap(Group::getId, Group::getName));

        return grants.stream()
            .map(g -> toDTO(g, groupNames.getOrDefault(g.getGroupId(), "unknown")))
            .toList();
    }

    private Document requireDocumentInTenant(UUID documentId, UUID tenantId) {
        return documentRepository.findByIdAndTenantId(documentId, tenantId)
            .orElseThrow(() -> new IllegalArgumentException("Document not found: " + documentId));
    }

    private Group requireGroupInTenant(UUID groupId, UUID tenantId) {
        return groupRepository.findByIdAndTenantId(groupId, tenantId)
            .orElseThrow(() -> new IllegalArgumentException("Group not found: " + groupId));
    }

    private GroupGranteeDTO toDTO(GroupDocumentAccess grant, String groupName) {
        return new GroupGranteeDTO(
            grant.getId().toString(),
            grant.getGroupId().toString(),
            groupName,
            grant.getGrantedBy().toString(),
            grant.getGrantedAt().toString()
        );
    }

    public record GroupGranteeDTO(String grantId, String groupId, String groupName, String grantedBy, String grantedAt) {}
}

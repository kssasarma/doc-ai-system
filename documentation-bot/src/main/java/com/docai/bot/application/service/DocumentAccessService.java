package com.docai.bot.application.service;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.docai.bot.domain.entity.Document;
import com.docai.bot.domain.entity.DocumentAccess;
import com.docai.bot.domain.entity.User;
import com.docai.bot.domain.repository.DocumentAccessRepository;
import com.docai.bot.domain.repository.DocumentRepository;
import com.docai.bot.domain.repository.UserRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Manages who has access to which document. This is the write side of access control;
 * {@link DocumentAccessPolicy} is the read side consulted at retrieval time.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DocumentAccessService {

    private final DocumentAccessRepository documentAccessRepository;
    private final DocumentRepository documentRepository;
    private final UserRepository userRepository;

    @Transactional
    public GranteeDTO grant(UUID documentId, UUID targetUserId, UUID tenantId, UUID grantedBy) {
        Document document = requireDocumentInTenant(documentId, tenantId);
        User targetUser = requireUserInTenant(targetUserId, tenantId);

        DocumentAccess grant = documentAccessRepository.findByDocumentIdAndUserId(documentId, targetUserId)
            .orElseGet(() -> DocumentAccess.builder()
                .documentId(document.getId())
                .userId(targetUser.getId())
                .tenantId(tenantId)
                .grantedBy(grantedBy)
                .build());

        // flush (not just save) so the @CreationTimestamp-generated grantedAt is populated
        // in-memory before it's read below — Hibernate doesn't guarantee that until a flush.
        DocumentAccess saved = documentAccessRepository.saveAndFlush(grant);
        log.info("Granted document {} access to user {} (tenant {})", documentId, targetUserId, tenantId);
        return toDTO(saved, targetUser.getUsername());
    }

    @Transactional
    public void revoke(UUID documentId, UUID targetUserId, UUID tenantId) {
        requireDocumentInTenant(documentId, tenantId);
        documentAccessRepository.deleteByDocumentIdAndUserId(documentId, targetUserId);
        log.info("Revoked document {} access from user {} (tenant {})", documentId, targetUserId, tenantId);
    }

    @Transactional(readOnly = true)
    public List<GranteeDTO> listGrantees(UUID documentId, UUID tenantId) {
        requireDocumentInTenant(documentId, tenantId);

        List<DocumentAccess> grants = documentAccessRepository.findByDocumentIdAndTenantId(documentId, tenantId);
        Map<UUID, String> usernames = userRepository.findAllById(
                grants.stream().map(DocumentAccess::getUserId).toList())
            .stream()
            .collect(Collectors.toMap(User::getId, User::getUsername));

        return grants.stream()
            .map(g -> toDTO(g, usernames.getOrDefault(g.getUserId(), "unknown")))
            .toList();
    }

    private Document requireDocumentInTenant(UUID documentId, UUID tenantId) {
        return documentRepository.findByIdAndTenantId(documentId, tenantId)
            .orElseThrow(() -> new IllegalArgumentException("Document not found: " + documentId));
    }

    private User requireUserInTenant(UUID userId, UUID tenantId) {
        return userRepository.findByIdAndTenantId(userId, tenantId)
            .orElseThrow(() -> new IllegalArgumentException("User not found in this tenant: " + userId));
    }

    private GranteeDTO toDTO(DocumentAccess grant, String username) {
        return new GranteeDTO(
            grant.getId().toString(),
            grant.getUserId().toString(),
            username,
            grant.getGrantedBy().toString(),
            grant.getGrantedAt().toString()
        );
    }

    public record GranteeDTO(String grantId, String userId, String username, String grantedBy, String grantedAt) {}
}

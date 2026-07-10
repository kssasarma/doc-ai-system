package com.docai.bot.application.service;

import java.util.Set;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.docai.bot.domain.entity.User;
import com.docai.bot.domain.model.SearchScope;
import com.docai.bot.domain.repository.DocumentAccessRepository;
import com.docai.bot.domain.repository.DocumentRepository;
import com.docai.bot.domain.repository.UserRepository;

import lombok.RequiredArgsConstructor;

/**
 * Default {@link DocumentAccessPolicy}: a USER sees exactly the documents explicitly granted to
 * them via {@link DocumentAccessService}; an ADMIN (the tenant's own document manager) implicitly
 * sees their whole tenant's corpus without needing a personal grant on every document.
 */
@Service
@RequiredArgsConstructor
public class GrantBasedDocumentAccessPolicy implements DocumentAccessPolicy {

    private final DocumentAccessRepository documentAccessRepository;
    private final DocumentRepository documentRepository;
    private final UserRepository userRepository;

    @Override
    @Transactional(readOnly = true)
    public SearchScope resolveScope(UUID userId, UUID tenantId) {
        User user = userRepository.findById(userId)
            .orElseThrow(() -> new IllegalArgumentException("User not found: " + userId));

        Set<UUID> documentIds = user.getRole() == User.Role.ADMIN
            ? documentRepository.findIdsByTenantId(tenantId)
            : documentAccessRepository.findAccessibleDocumentIds(userId, tenantId);

        return new SearchScope(tenantId, documentIds);
    }
}

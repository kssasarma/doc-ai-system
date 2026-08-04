package com.docai.bot.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.docai.bot.application.service.GrantBasedDocumentAccessPolicy;
import com.docai.bot.domain.entity.Notebook;
import com.docai.bot.domain.model.SearchScope;
import com.docai.bot.domain.repository.DocumentAccessRepository;
import com.docai.bot.domain.repository.DocumentRepository;
import com.docai.bot.domain.repository.GroupDocumentAccessRepository;
import com.docai.bot.domain.repository.NotebookRepository;
import com.docai.bot.domain.repository.UserRepository;

/**
 * Personal notebooks are meant to be a strictly private universe (see SearchScope#resolveNotebookScope
 * javadoc) — these tests exist mainly to pin down the one thing that must never regress: a
 * notebookId that isn't owned by the calling user must resolve to an empty scope, never someone
 * else's documents.
 */
@ExtendWith(MockitoExtension.class)
class GrantBasedDocumentAccessPolicyTest {

    @Mock DocumentAccessRepository documentAccessRepository;
    @Mock GroupDocumentAccessRepository groupDocumentAccessRepository;
    @Mock DocumentRepository documentRepository;
    @Mock UserRepository userRepository;
    @Mock NotebookRepository notebookRepository;

    private GrantBasedDocumentAccessPolicy policy() {
        return new GrantBasedDocumentAccessPolicy(
            documentAccessRepository, groupDocumentAccessRepository, documentRepository, userRepository, notebookRepository);
    }

    @Test
    void resolveNotebookScope_ownedNotebook_returnsItsDocumentIds() {
        UUID userId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        UUID notebookId = UUID.randomUUID();
        UUID docId = UUID.randomUUID();

        when(notebookRepository.findByIdAndTenantIdAndOwnerId(notebookId, tenantId, userId))
            .thenReturn(Optional.of(Notebook.builder().id(notebookId).tenantId(tenantId).ownerId(userId).name("n").build()));
        when(documentRepository.findIdsByNotebookIdAndOwnerId(tenantId, notebookId, userId))
            .thenReturn(Set.of(docId));

        SearchScope scope = policy().resolveNotebookScope(userId, tenantId, notebookId);

        assertThat(scope.documentIds()).containsExactly(docId);
        assertThat(scope.tenantId()).isEqualTo(tenantId);
    }

    @Test
    void resolveNotebookScope_notOwnedNotebook_returnsEmptyScopeWithoutLeakingDocuments() {
        UUID userId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        UUID notebookId = UUID.randomUUID();

        when(notebookRepository.findByIdAndTenantIdAndOwnerId(notebookId, tenantId, userId))
            .thenReturn(Optional.empty());

        SearchScope scope = policy().resolveNotebookScope(userId, tenantId, notebookId);

        assertThat(scope.isEmpty()).isTrue();
        org.mockito.Mockito.verify(documentRepository, org.mockito.Mockito.never())
            .findIdsByNotebookIdAndOwnerId(any(), any(), any());
    }
}

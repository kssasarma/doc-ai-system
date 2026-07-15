package com.docai.bot.application.service;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.docai.bot.domain.entity.Document;
import com.docai.bot.domain.model.SearchScope;
import com.docai.bot.domain.repository.DocumentRepository;

import lombok.RequiredArgsConstructor;

/**
 * The "Google for the company" document library (Phase 6.2) — every document a caller can
 * actually search, for the new {@code /library} page and the home screen's "recently updated"
 * strip. Deliberately scoped through {@link SearchScope}, same reasoning as
 * {@link ProductCatalogService}: never reveal a document that lives outside the caller's access.
 */
@Service
@RequiredArgsConstructor
public class DocumentLibraryService {

    private final DocumentRepository documentRepository;

    public record LibraryDocument(
        String id, String documentName, String product, String version,
        Integer chunkCount, String updatedAt) {}

    @Transactional(readOnly = true)
    public List<LibraryDocument> listAccessibleDocuments(SearchScope scope, String query) {
        if (scope.isEmpty()) {
            return List.of();
        }
        List<Document> docs = documentRepository.findAccessibleCompletedDocuments(scope.tenantId(), scope.documentIds());
        String needle = query != null ? query.trim().toLowerCase() : "";
        return docs.stream()
            .filter(d -> needle.isEmpty()
                || d.getDocumentName().toLowerCase().contains(needle)
                || d.getProduct().toLowerCase().contains(needle))
            .map(d -> new LibraryDocument(
                d.getId().toString(), d.getDocumentName(), d.getProduct(), d.getVersion(),
                d.getChunkCount(), d.getUpdatedAt() != null ? d.getUpdatedAt().toString() : null))
            .toList();
    }
}

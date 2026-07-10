package com.docai.bot.application.service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.docai.bot.domain.model.SearchScope;
import com.docai.bot.domain.model.VersionComparator;
import com.docai.bot.domain.repository.DocumentRepository;

import lombok.RequiredArgsConstructor;

/**
 * Enumerates the product+version pairs a caller can actually search — backs the chat UI's
 * optional scope chip (Phase 7). Deliberately scoped through {@link SearchScope}, not the older
 * unscoped {@code DocumentRepository.findDistinctProducts()}/{@code findVersionsByProduct()}
 * queries {@link QueryAnalyzerService} uses internally for LLM-extracted-text disambiguation —
 * this enumeration is user-facing and must never reveal the existence of a product/version that
 * lives only in documents the caller can't access.
 */
@Service
@RequiredArgsConstructor
public class ProductCatalogService {

    private final DocumentRepository documentRepository;

    public record ProductEntry(String product, List<String> versions) {}

    @Transactional(readOnly = true)
    public List<ProductEntry> listAccessibleProducts(SearchScope scope) {
        if (scope.isEmpty()) {
            return List.of();
        }

        Map<String, List<String>> byProduct = new LinkedHashMap<>();
        for (var row : documentRepository.findDistinctProductVersionsAccessible(scope.tenantId(), scope.documentIds())) {
            if (row.getProduct() == null) continue;
            byProduct.computeIfAbsent(row.getProduct(), p -> new java.util.ArrayList<>());
            if (row.getVersion() != null) {
                byProduct.get(row.getProduct()).add(row.getVersion());
            }
        }

        return byProduct.entrySet().stream()
            .sorted(Map.Entry.comparingByKey())
            .map(e -> new ProductEntry(e.getKey(), e.getValue().stream().sorted(VersionComparator.INSTANCE).toList()))
            .toList();
    }
}

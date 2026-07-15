package com.docai.ingestor.application.service;

import java.util.UUID;

import org.springframework.stereotype.Service;

import com.docai.ingestor.domain.entity.Document.IngestionStatus;
import com.docai.ingestor.domain.entity.Tenant;
import com.docai.ingestor.domain.repository.DocumentRepository;
import com.docai.ingestor.domain.repository.TenantRepository;

import lombok.RequiredArgsConstructor;

/**
 * Shared tenant document-quota guard. Originally only checked in {@code DocumentUploadController}
 * — Confluence/Notion connector sync and webhook ingestion created documents with no quota check
 * at all, letting a tenant blow past its plan's {@code maxDocuments} limit through any path other
 * than a direct upload. Every document-creating path calls this first.
 */
@Service
@RequiredArgsConstructor
public class DocumentQuotaService {

    private final TenantRepository tenantRepository;
    private final DocumentRepository documentRepository;

    /** @throws TenantQuotaExceededException if the tenant is at or over its plan's document limit. */
    public void checkQuota(UUID tenantId) {
        Tenant tenant = tenantRepository.findById(tenantId)
            .orElseThrow(() -> new IllegalStateException("Tenant not found: " + tenantId));
        long currentDocuments = documentRepository.countByTenantIdAndStatusNot(tenantId, IngestionStatus.FAILED);
        if (currentDocuments >= tenant.getMaxDocuments()) {
            throw new TenantQuotaExceededException(
                "This tenant has reached its plan limit of " + tenant.getMaxDocuments() + " documents");
        }
    }
}

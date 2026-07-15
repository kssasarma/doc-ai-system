package com.docai.ingestor.domain.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.docai.ingestor.domain.entity.WebhookEvent;

public interface WebhookEventRepository extends JpaRepository<WebhookEvent, UUID> {

    /** Used to reflect the real ingestion outcome back onto the triggering webhook event once
     * ingestion actually finishes — see IngestionEventListener. Typically zero or one result;
     * a list in case a document was ever re-triggered via more than one webhook event. */
    List<WebhookEvent> findByDocumentIdAndStatus(UUID documentId, WebhookEvent.Status status);
}

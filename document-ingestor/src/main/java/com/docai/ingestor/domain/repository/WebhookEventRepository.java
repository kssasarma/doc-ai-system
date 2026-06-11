package com.docai.ingestor.domain.repository;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.docai.ingestor.domain.entity.WebhookEvent;

public interface WebhookEventRepository extends JpaRepository<WebhookEvent, UUID> {
}

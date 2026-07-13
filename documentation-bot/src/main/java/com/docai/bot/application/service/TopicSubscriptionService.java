package com.docai.bot.application.service;

import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.docai.bot.domain.entity.Notification;
import com.docai.bot.domain.entity.TopicSubscription;
import com.docai.bot.domain.model.SearchScope;
import com.docai.bot.domain.repository.DocumentRepository;
import com.docai.bot.domain.repository.NotificationRepository;
import com.docai.bot.domain.repository.TopicSubscriptionRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Phase 6.6 — Proactive Topic Subscriptions.
 * Users subscribe to topics per product/version. When new documents are
 * ingested that match a subscription, a notification is created.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TopicSubscriptionService {

    private static final double NOTIFY_THRESHOLD = 0.35;

    private final TopicSubscriptionRepository subscriptionRepository;
    private final NotificationRepository notificationRepository;
    private final VectorSearchService vectorSearchService;
    private final DocumentRepository documentRepository;

    @Transactional
    public TopicSubscription subscribe(UUID userId, UUID tenantId, String topic, String product, String version) {
        subscriptionRepository.findByUserIdAndTopicAndProductAndVersion(userId, topic, product, version)
            .ifPresent(existing -> { throw new IllegalStateException("Already subscribed to this topic"); });

        TopicSubscription sub = TopicSubscription.builder()
            .userId(userId)
            .tenantId(tenantId)
            .topic(topic)
            .product(product)
            .version(version)
            .build();
        return subscriptionRepository.save(sub);
    }

    @Transactional
    public void unsubscribe(UUID subscriptionId, UUID userId) {
        TopicSubscription sub = subscriptionRepository.findById(subscriptionId)
            .orElseThrow(() -> new IllegalArgumentException("Subscription not found"));
        if (!sub.getUserId().equals(userId)) {
            throw new SecurityException("Not authorized to delete this subscription");
        }
        subscriptionRepository.delete(sub);
    }

    @Transactional(readOnly = true)
    public List<TopicSubscription> getSubscriptions(UUID userId) {
        return subscriptionRepository.findByUserId(userId);
    }

    /**
     * Called by the ingestion pipeline (or a webhook) after new documents land.
     * Checks all subscriptions for the product/version and notifies matched users.
     */
    @Transactional
    public void notifySubscribersForProduct(UUID tenantId, String product, String version, String documentName) {
        List<TopicSubscription> subs = version != null
            ? subscriptionRepository.findByTenantIdAndProductAndVersion(tenantId, product, version)
            : subscriptionRepository.findByTenantIdAndProduct(tenantId, product);

        if (subs.isEmpty()) return;

        SearchScope scope = new SearchScope(tenantId, documentRepository.findIdsByTenantId(tenantId))
            .withVersionNarrow(product, version);
        for (TopicSubscription sub : subs) {
            try {
                // Use vector search to check if new document content is relevant to subscribed topic
                List<com.docai.bot.domain.model.RetrievedChunk> chunks =
                    vectorSearchService.search(sub.getTopic(), scope);

                boolean relevant = chunks.stream()
                    .anyMatch(c -> c.getSimilarity() >= NOTIFY_THRESHOLD
                        && documentName.equals(c.getDocumentName()));

                if (relevant) {
                    createNotification(sub.getUserId(), sub.getTopic(), product, version, documentName);
                }
            } catch (Exception e) {
                log.warn("Failed to check subscription {} for user {}: {}",
                    sub.getId(), sub.getUserId(), e.getMessage());
            }
        }
    }

    private void createNotification(UUID userId, String topic, String product,
                                    String version, String documentName) {
        String title = String.format("Docs updated: %s %s", product, version != null ? version : "");
        String body  = String.format(
            "Documentation matching your subscription '%s' was updated (document: %s)",
            topic, documentName);

        Notification notification = Notification.builder()
            .userId(userId)
            .type("SUBSCRIPTION_MATCH")
            .title(title)
            .body(body)
            .build();
        notificationRepository.save(notification);
        log.info("Notified user {} about subscription match: {}", userId, topic);
    }
}

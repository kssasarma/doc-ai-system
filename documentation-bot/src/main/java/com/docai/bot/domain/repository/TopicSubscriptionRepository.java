package com.docai.bot.domain.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.docai.bot.domain.entity.TopicSubscription;

@Repository
public interface TopicSubscriptionRepository extends JpaRepository<TopicSubscription, UUID> {

    List<TopicSubscription> findByUserId(UUID userId);

    List<TopicSubscription> findByTenantIdAndProductAndVersion(UUID tenantId, String product, String version);

    List<TopicSubscription> findByTenantIdAndProduct(UUID tenantId, String product);

    Optional<TopicSubscription> findByUserIdAndTopicAndProductAndVersion(
        UUID userId, String topic, String product, String version);

    void deleteByUserId(UUID userId);
}

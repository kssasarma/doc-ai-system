package com.docai.ingestor.domain.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.docai.ingestor.domain.entity.IntegrationToken;
import com.docai.ingestor.domain.entity.IntegrationToken.Provider;

public interface IntegrationTokenRepository extends JpaRepository<IntegrationToken, UUID> {
    List<IntegrationToken> findByUserId(UUID userId);
    Optional<IntegrationToken> findByUserIdAndProvider(UUID userId, Provider provider);
}

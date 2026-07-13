package com.docai.bot.domain.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import com.docai.bot.domain.entity.ApiKey;

public interface ApiKeyRepository extends JpaRepository<ApiKey, UUID> {

    Optional<ApiKey> findByKeyHash(String keyHash);

    List<ApiKey> findByUserIdOrderByCreatedAtDesc(UUID userId);

    void deleteByUserId(UUID userId);

    @Modifying
    @Query("UPDATE ApiKey k SET k.lastUsedAt = CURRENT_TIMESTAMP WHERE k.id = :id")
    void updateLastUsed(UUID id);
}

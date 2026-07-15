package com.docai.bot.domain.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.docai.bot.domain.entity.SharedChatLink;

@Repository
public interface SharedChatLinkRepository extends JpaRepository<SharedChatLink, UUID> {
    Optional<SharedChatLink> findByToken(String token);
    Optional<SharedChatLink> findByChatId(UUID chatId);
    boolean existsByChatId(UUID chatId);
    void deleteByChatId(UUID chatId);
    void deleteByTenantId(UUID tenantId);
    void deleteByCreatedBy(UUID createdBy);
}

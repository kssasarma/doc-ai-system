package com.docai.bot.domain.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.docai.bot.domain.entity.SharedChatRecipient;

public interface SharedChatRecipientRepository extends JpaRepository<SharedChatRecipient, UUID> {

    List<SharedChatRecipient> findByLinkId(UUID linkId);

    Optional<SharedChatRecipient> findByLinkIdAndUserId(UUID linkId, UUID userId);

    boolean existsByLinkIdAndUserId(UUID linkId, UUID userId);

    void deleteByLinkIdAndUserId(UUID linkId, UUID userId);

    void deleteByLinkId(UUID linkId);

    void deleteByUserId(UUID userId);
}

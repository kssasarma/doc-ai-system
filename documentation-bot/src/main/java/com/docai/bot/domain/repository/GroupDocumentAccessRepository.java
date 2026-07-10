package com.docai.bot.domain.repository;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import com.docai.bot.domain.entity.GroupDocumentAccess;

@Repository
public interface GroupDocumentAccessRepository extends JpaRepository<GroupDocumentAccess, UUID> {

    Optional<GroupDocumentAccess> findByDocumentIdAndGroupId(UUID documentId, UUID groupId);

    List<GroupDocumentAccess> findByDocumentIdAndTenantId(UUID documentId, UUID tenantId);

    void deleteByDocumentIdAndGroupId(UUID documentId, UUID groupId);

    /** Document IDs accessible to {@code userId} via ANY group they belong to — a plain
     * WHERE-clause join across two otherwise-unrelated entities (no JPA association is declared
     * between {@link GroupDocumentAccess} and {@link com.docai.bot.domain.entity.GroupMembership},
     * matching this codebase's existing convention of enforcing cross-entity relationships at the
     * query/application layer rather than via mapped associations — see {@code DocumentAccess}). */
    @Query("SELECT gda.documentId FROM GroupDocumentAccess gda, GroupMembership gm " +
           "WHERE gm.groupId = gda.groupId AND gm.userId = :userId AND gda.tenantId = :tenantId")
    Set<UUID> findAccessibleDocumentIdsViaGroups(UUID userId, UUID tenantId);
}

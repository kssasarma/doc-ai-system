package com.docai.bot.domain.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.docai.bot.domain.entity.GroupMembership;

@Repository
public interface GroupMembershipRepository extends JpaRepository<GroupMembership, UUID> {

    List<GroupMembership> findByGroupId(UUID groupId);

    long countByGroupId(UUID groupId);

    boolean existsByGroupIdAndUserId(UUID groupId, UUID userId);

    void deleteByGroupIdAndUserId(UUID groupId, UUID userId);

    void deleteByUserId(UUID userId);
}

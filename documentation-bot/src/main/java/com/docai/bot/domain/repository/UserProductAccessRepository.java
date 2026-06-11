package com.docai.bot.domain.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.docai.bot.domain.entity.UserProductAccess;

@Repository
public interface UserProductAccessRepository extends JpaRepository<UserProductAccess, UUID> {

    List<UserProductAccess> findByUserId(UUID userId);

    List<UserProductAccess> findByProductAndVersion(String product, String version);

    Optional<UserProductAccess> findByUserIdAndProductAndVersion(UUID userId, String product, String version);

    boolean existsByUserIdAndProductAndVersion(UUID userId, String product, String version);

    void deleteByUserId(UUID userId);
}

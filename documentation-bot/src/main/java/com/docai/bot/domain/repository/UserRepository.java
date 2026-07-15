package com.docai.bot.domain.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import com.docai.bot.domain.entity.User;

@Repository
public interface UserRepository extends JpaRepository<User, UUID> {
    Optional<User> findByUsername(String username);
    Optional<User> findByEmail(String email);
    boolean existsByUsername(String username);
    boolean existsByEmail(String email);
    Optional<User> findByOidcSubAndOidcProvider(String oidcSub, String oidcProvider);
    List<User> findByTenantId(UUID tenantId);
    Optional<User> findByIdAndTenantId(UUID id, UUID tenantId);
    List<User> findByTenantIdAndRole(UUID tenantId, User.Role role);
    long countByTenantIdAndRoleAndDeactivatedAtIsNull(UUID tenantId, User.Role role);

    // Phase 6.4 — backs both the paginated Users admin page and the async-search document/group
    // grant pickers (Combobox). q is optional: null/blank matches everything.
    @Query("""
        SELECT u FROM User u
        WHERE u.tenantId = :tenantId
          AND (:q IS NULL OR :q = '' OR LOWER(u.username) LIKE LOWER(CONCAT('%', :q, '%'))
               OR LOWER(u.email) LIKE LOWER(CONCAT('%', :q, '%')))
        """)
    Page<User> searchByTenantId(UUID tenantId, String q, Pageable pageable);
}

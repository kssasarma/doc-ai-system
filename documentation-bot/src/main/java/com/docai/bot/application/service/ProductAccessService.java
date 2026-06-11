package com.docai.bot.application.service;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.docai.bot.domain.entity.User;
import com.docai.bot.domain.entity.UserProductAccess;
import com.docai.bot.domain.repository.UserProductAccessRepository;
import com.docai.bot.domain.repository.UserRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class ProductAccessService {

    private final UserProductAccessRepository accessRepository;
    private final UserRepository userRepository;

    @Transactional(readOnly = true)
    public List<UserWithAccessDTO> getAllUsersWithAccess() {
        List<User> users = userRepository.findAll();
        Map<UUID, List<UserProductAccess>> grantsByUser = accessRepository.findAll().stream()
            .collect(Collectors.groupingBy(UserProductAccess::getUserId));
        Map<UUID, String> usernameMap = users.stream()
            .collect(Collectors.toMap(User::getId, User::getUsername));

        return users.stream()
            .map(u -> {
                List<ProductAccessGrantDTO> grants = grantsByUser
                    .getOrDefault(u.getId(), List.of()).stream()
                    .map(g -> toGrantDTO(g, usernameMap))
                    .collect(Collectors.toList());
                return UserWithAccessDTO.builder()
                    .userId(u.getId().toString())
                    .username(u.getUsername())
                    .email(u.getEmail())
                    .role(u.getRole().name())
                    .grants(grants)
                    .build();
            })
            .collect(Collectors.toList());
    }

    @Transactional
    public ProductAccessGrantDTO grantAccess(UUID targetUserId, String product, String version,
                                             UUID grantedBy) {
        // Upsert: delete existing matching grant then save fresh
        accessRepository.findByUserIdAndProductAndVersion(targetUserId, product, version)
            .ifPresent(existing -> accessRepository.delete(existing));

        UserProductAccess grant = UserProductAccess.builder()
            .userId(targetUserId)
            .product(product)
            .version(version)
            .grantedBy(grantedBy)
            .build();
        UserProductAccess saved = accessRepository.save(grant);

        Map<UUID, String> usernameMap = userRepository.findAll().stream()
            .collect(Collectors.toMap(User::getId, User::getUsername));

        log.info("Granted access: user={} product={} version={}", targetUserId, product, version);
        return toGrantDTO(saved, usernameMap);
    }

    @Transactional
    public void revokeAccess(UUID grantId, UUID actorId) {
        UserProductAccess grant = accessRepository.findById(grantId)
            .orElseThrow(() -> new IllegalArgumentException("Grant not found"));
        accessRepository.delete(grant);
        log.info("Revoked grant {} by actor {}", grantId, actorId);
    }

    private ProductAccessGrantDTO toGrantDTO(UserProductAccess g, Map<UUID, String> usernameMap) {
        return ProductAccessGrantDTO.builder()
            .id(g.getId().toString())
            .userId(g.getUserId().toString())
            .username(usernameMap.getOrDefault(g.getUserId(), "unknown"))
            .product(g.getProduct())
            .version(g.getVersion())
            .grantedBy(g.getGrantedBy() != null ? g.getGrantedBy().toString() : null)
            .createdAt(g.getCreatedAt() != null ? g.getCreatedAt().toString() : null)
            .build();
    }

    @lombok.Data @lombok.Builder
    public static class ProductAccessGrantDTO {
        private String id;
        private String userId;
        private String username;
        private String product;
        private String version;
        private String grantedBy;
        private String createdAt;
    }

    @lombok.Data @lombok.Builder
    public static class UserWithAccessDTO {
        private String userId;
        private String username;
        private String email;
        private String role;
        private List<ProductAccessGrantDTO> grants;
    }
}

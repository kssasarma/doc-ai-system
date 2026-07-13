package com.docai.bot.application.service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.docai.bot.domain.entity.AuditLog;
import com.docai.bot.domain.entity.User;
import com.docai.bot.domain.repository.AuditLogRepository;
import com.docai.bot.domain.repository.UserRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuditLogService {

    private final AuditLogRepository auditLogRepository;
    private final UserRepository userRepository;

    @Async
    @Transactional
    public void log(UUID actorId, UUID tenantId, String action, String targetType, UUID targetId,
                    String metadata, String ipAddress) {
        try {
            auditLogRepository.save(AuditLog.builder()
                .actorId(actorId)
                .tenantId(tenantId)
                .action(action)
                .targetType(targetType)
                .targetId(targetId)
                .metadata(metadata)
                .ipAddress(ipAddress)
                .build());
        } catch (Exception e) {
            log.warn("Failed to write audit log entry [{}]: {}", action, e.getMessage());
        }
    }

    @Transactional(readOnly = true)
    public Page<AuditLogDTO> getAuditLog(UUID tenantId, int page, int size, String action, String since) {
        Pageable pageable = PageRequest.of(page, size);
        LocalDateTime sinceTime = since != null && !since.isBlank()
            ? LocalDateTime.parse(since) : null;

        Page<AuditLog> raw;
        if (action != null && !action.isBlank() && sinceTime != null) {
            raw = auditLogRepository.findByTenantIdAndActionAndCreatedAtAfterOrderByCreatedAtDesc(tenantId, action, sinceTime, pageable);
        } else if (action != null && !action.isBlank()) {
            raw = auditLogRepository.findByTenantIdAndActionOrderByCreatedAtDesc(tenantId, action, pageable);
        } else if (sinceTime != null) {
            raw = auditLogRepository.findByTenantIdAndCreatedAtAfterOrderByCreatedAtDesc(tenantId, sinceTime, pageable);
        } else {
            raw = auditLogRepository.findByTenantIdOrderByCreatedAtDesc(tenantId, pageable);
        }

        Map<UUID, String> usernameMap = userRepository.findByTenantId(tenantId).stream()
            .collect(Collectors.toMap(User::getId, User::getUsername));

        List<AuditLogDTO> dtos = raw.getContent().stream()
            .map(entry -> AuditLogDTO.builder()
                .id(entry.getId().toString())
                .actorId(entry.getActorId() != null ? entry.getActorId().toString() : null)
                .actorUsername(entry.getActorId() != null
                    ? usernameMap.get(entry.getActorId()) : null)
                .action(entry.getAction())
                .targetType(entry.getTargetType())
                .targetId(entry.getTargetId() != null ? entry.getTargetId().toString() : null)
                .metadata(entry.getMetadata())
                .ipAddress(entry.getIpAddress())
                .createdAt(entry.getCreatedAt().toString())
                .build())
            .collect(Collectors.toList());

        return new PageImpl<>(dtos, pageable, raw.getTotalElements());
    }

    @lombok.Data @lombok.Builder
    public static class AuditLogDTO {
        private String id;
        private String actorId;
        private String actorUsername;
        private String action;
        private String targetType;
        private String targetId;
        private String metadata;
        private String ipAddress;
        private String createdAt;
    }
}

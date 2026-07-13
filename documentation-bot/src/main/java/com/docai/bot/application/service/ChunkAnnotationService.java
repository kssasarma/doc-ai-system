package com.docai.bot.application.service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.docai.bot.config.UserPrincipal;
import com.docai.bot.domain.entity.ChunkAnnotation;
import com.docai.bot.domain.entity.Document;
import com.docai.bot.domain.entity.DocumentChunk;
import com.docai.bot.domain.entity.User;
import com.docai.bot.domain.repository.ChunkAnnotationRepository;
import com.docai.bot.domain.repository.DocumentChunkRepository;
import com.docai.bot.domain.repository.DocumentRepository;
import com.docai.bot.domain.repository.UserRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class ChunkAnnotationService {

    private final ChunkAnnotationRepository annotationRepository;
    private final UserRepository userRepository;
    private final DocumentChunkRepository chunkRepository;
    private final DocumentRepository documentRepository;

    @Transactional
    public AnnotationDTO createAnnotation(UUID chunkId, UserPrincipal principal, String body) {
        UUID tenantId = assertChunkAccess(chunkId, principal);
        ChunkAnnotation annotation = ChunkAnnotation.builder()
            .documentChunkId(chunkId)
            .userId(principal.userId())
            .tenantId(tenantId)
            .body(body)
            .build();
        return toDTO(annotationRepository.save(annotation));
    }

    @Transactional(readOnly = true)
    public List<AnnotationDTO> listAnnotations(UUID chunkId, UserPrincipal principal) {
        assertChunkAccess(chunkId, principal);
        return annotationRepository.findByDocumentChunkIdOrderByCreatedAtAsc(chunkId)
            .stream()
            .map(this::toDTO)
            .collect(Collectors.toList());
    }

    @Transactional
    public void deleteAnnotation(UUID annotationId, UserPrincipal principal) {
        ChunkAnnotation annotation = annotationRepository.findById(annotationId)
            .orElseThrow(() -> new IllegalArgumentException("Annotation not found"));
        boolean isOwner = annotation.getUserId().equals(principal.userId());
        boolean isTenantAdmin = principal.isSuperAdmin()
            || (principal.isAdmin() && principal.tenantId() != null && principal.tenantId().equals(annotation.getTenantId()));
        if (!isOwner && !isTenantAdmin) {
            throw new AccessDeniedException("Not authorized");
        }
        annotationRepository.delete(annotation);
    }

    /** Verifies the chunk's owning document belongs to the caller's tenant, and returns that tenant id. */
    private UUID assertChunkAccess(UUID chunkId, UserPrincipal principal) {
        DocumentChunk chunk = chunkRepository.findById(chunkId)
            .orElseThrow(() -> new IllegalArgumentException("Chunk not found"));
        Document document = documentRepository.findById(chunk.getDocumentId())
            .orElseThrow(() -> new IllegalArgumentException("Document not found"));
        if (!principal.isSuperAdmin() && !document.getTenantId().equals(principal.tenantId())) {
            throw new AccessDeniedException("You do not have access to this document chunk");
        }
        return document.getTenantId();
    }

    private AnnotationDTO toDTO(ChunkAnnotation a) {
        String username = userRepository.findById(a.getUserId())
            .map(User::getUsername).orElse("unknown");
        return AnnotationDTO.builder()
            .id(a.getId().toString())
            .documentChunkId(a.getDocumentChunkId().toString())
            .userId(a.getUserId().toString())
            .username(username)
            .body(a.getBody())
            .createdAt(a.getCreatedAt())
            .updatedAt(a.getUpdatedAt())
            .build();
    }

    @lombok.Data @lombok.Builder
    public static class AnnotationDTO {
        private String id;
        private String documentChunkId;
        private String userId;
        private String username;
        private String body;
        private LocalDateTime createdAt;
        private LocalDateTime updatedAt;
    }
}

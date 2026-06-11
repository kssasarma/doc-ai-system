package com.docai.bot.application.service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.docai.bot.domain.entity.ChunkAnnotation;
import com.docai.bot.domain.entity.User;
import com.docai.bot.domain.repository.ChunkAnnotationRepository;
import com.docai.bot.domain.repository.UserRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class ChunkAnnotationService {

    private final ChunkAnnotationRepository annotationRepository;
    private final UserRepository userRepository;

    @Transactional
    public AnnotationDTO createAnnotation(UUID chunkId, UUID userId, String body) {
        ChunkAnnotation annotation = ChunkAnnotation.builder()
            .documentChunkId(chunkId)
            .userId(userId)
            .body(body)
            .build();
        return toDTO(annotationRepository.save(annotation));
    }

    @Transactional(readOnly = true)
    public List<AnnotationDTO> listAnnotations(UUID chunkId) {
        return annotationRepository.findByDocumentChunkIdOrderByCreatedAtAsc(chunkId)
            .stream()
            .map(this::toDTO)
            .collect(Collectors.toList());
    }

    @Transactional
    public void deleteAnnotation(UUID annotationId, UUID userId, boolean isAdmin) {
        ChunkAnnotation annotation = annotationRepository.findById(annotationId)
            .orElseThrow(() -> new IllegalArgumentException("Annotation not found"));
        if (!isAdmin && !annotation.getUserId().equals(userId)) {
            throw new IllegalStateException("Not authorized");
        }
        annotationRepository.delete(annotation);
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

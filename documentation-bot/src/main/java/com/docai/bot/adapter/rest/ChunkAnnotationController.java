package com.docai.bot.adapter.rest;

import java.util.List;
import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.docai.bot.application.service.ChunkAnnotationService;
import com.docai.bot.application.service.ChunkAnnotationService.AnnotationDTO;
import com.docai.bot.config.UserPrincipal;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/chunks")
@RequiredArgsConstructor
public class ChunkAnnotationController {

    private final ChunkAnnotationService annotationService;

    @GetMapping("/{chunkId}/annotations")
    public ResponseEntity<List<AnnotationDTO>> listAnnotations(@PathVariable String chunkId) {
        return ResponseEntity.ok(annotationService.listAnnotations(UUID.fromString(chunkId)));
    }

    @PostMapping("/{chunkId}/annotations")
    public ResponseEntity<AnnotationDTO> createAnnotation(
            @PathVariable String chunkId,
            @Valid @RequestBody CreateAnnotationRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {

        return ResponseEntity.ok(annotationService.createAnnotation(
            UUID.fromString(chunkId), principal.userId(), request.getBody()));
    }

    @DeleteMapping("/{chunkId}/annotations/{annotationId}")
    public ResponseEntity<Void> deleteAnnotation(
            @PathVariable String chunkId,
            @PathVariable String annotationId,
            @AuthenticationPrincipal UserPrincipal principal) {

        annotationService.deleteAnnotation(
            UUID.fromString(annotationId), principal.userId(), principal.isAdmin());
        return ResponseEntity.noContent().build();
    }

    @Data
    static class CreateAnnotationRequest {
        @NotBlank(message = "body is required")
        private String body;
    }
}

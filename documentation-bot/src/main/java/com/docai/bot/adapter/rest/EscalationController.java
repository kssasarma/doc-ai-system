package com.docai.bot.adapter.rest;

import java.util.List;
import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.docai.bot.application.service.EscalationService;
import com.docai.bot.application.service.EscalationService.EscalationDTO;
import com.docai.bot.config.UserPrincipal;
import com.docai.bot.domain.entity.Escalation;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/escalations")
@RequiredArgsConstructor
public class EscalationController {

    private final EscalationService escalationService;

    @GetMapping
    public ResponseEntity<List<EscalationDTO>> listEscalations(@AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(escalationService.listEscalations(principal));
    }

    @PostMapping("/messages/{messageId}")
    public ResponseEntity<EscalationDTO> createEscalation(
            @PathVariable String messageId,
            @Valid @RequestBody CreateEscalationRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {

        return ResponseEntity.ok(escalationService.createEscalation(
            UUID.fromString(messageId),
            request.getQuestionText(),
            request.getAiAnswerText(),
            request.getProduct(),
            request.getVersion(),
            principal
        ));
    }

    @PatchMapping("/{id}/answer")
    public ResponseEntity<EscalationDTO> answerEscalation(
            @PathVariable String id,
            @Valid @RequestBody AnswerRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {

        return ResponseEntity.ok(escalationService.answerEscalation(
            UUID.fromString(id), principal, request.getExpertAnswer()));
    }

    @PatchMapping("/{id}/status")
    public ResponseEntity<EscalationDTO> updateStatus(
            @PathVariable String id,
            @RequestBody StatusRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {

        return ResponseEntity.ok(escalationService.updateStatus(
            UUID.fromString(id), principal,
            Escalation.Status.valueOf(request.getStatus())));
    }

    // ── DTOs ─────────────────────────────────────────────────────────────────

    @Data
    static class CreateEscalationRequest {
        @NotBlank(message = "questionText is required")
        private String questionText;
        private String aiAnswerText;
        private String product;
        private String version;
    }

    @Data
    static class AnswerRequest {
        @NotBlank(message = "expertAnswer is required")
        private String expertAnswer;
    }

    @Data
    static class StatusRequest {
        private String status; // PENDING | IN_REVIEW | CLOSED
    }
}

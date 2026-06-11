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

import com.docai.bot.application.service.TopicSubscriptionService;
import com.docai.bot.config.UserPrincipal;
import com.docai.bot.domain.entity.TopicSubscription;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/subscriptions")
@RequiredArgsConstructor
public class TopicSubscriptionController {

    private final TopicSubscriptionService subscriptionService;

    @GetMapping
    public ResponseEntity<List<TopicSubscription>> getSubscriptions(
            @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(subscriptionService.getSubscriptions(principal.userId()));
    }

    @PostMapping
    public ResponseEntity<TopicSubscription> subscribe(
            @Valid @RequestBody SubscribeRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {

        TopicSubscription sub = subscriptionService.subscribe(
            principal.userId(), request.getTopic(),
            request.getProduct(), request.getVersion()
        );
        return ResponseEntity.ok(sub);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> unsubscribe(
            @PathVariable UUID id,
            @AuthenticationPrincipal UserPrincipal principal) {

        subscriptionService.unsubscribe(id, principal.userId());
        return ResponseEntity.noContent().build();
    }

    @Data
    static class SubscribeRequest {
        @NotBlank private String topic;
        private String product;
        private String version;
    }
}

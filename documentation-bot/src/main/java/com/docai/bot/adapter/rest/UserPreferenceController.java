package com.docai.bot.adapter.rest;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.docai.bot.application.service.UserPreferenceService;
import com.docai.bot.config.UserPrincipal;
import com.docai.bot.domain.entity.UserPreference;

import lombok.Data;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/users/preferences")
@RequiredArgsConstructor
public class UserPreferenceController {

    private final UserPreferenceService preferenceService;

    @GetMapping
    public ResponseEntity<PreferenceResponse> getPreferences(
            @AuthenticationPrincipal UserPrincipal principal) {
        UserPreference prefs = preferenceService.getPreferences(principal.userId());
        return ResponseEntity.ok(toResponse(prefs));
    }

    @PutMapping
    public ResponseEntity<PreferenceResponse> updatePreferences(
            @RequestBody PreferenceRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {

        UserPreference saved = preferenceService.savePreferences(
            principal.userId(),
            request.getVerbosity(),
            request.getAnswerFormat()
        );
        return ResponseEntity.ok(toResponse(saved));
    }

    private PreferenceResponse toResponse(UserPreference p) {
        PreferenceResponse r = new PreferenceResponse();
        r.setVerbosity(p.getVerbosity());
        r.setAnswerFormat(p.getAnswerFormat());
        return r;
    }

    @Data
    static class PreferenceRequest {
        private String verbosity;
        private String answerFormat;
    }

    @Data
    static class PreferenceResponse {
        private String verbosity;
        private String answerFormat;
    }
}

package com.docai.bot.application.service;

import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.docai.bot.domain.entity.UserPreference;
import com.docai.bot.domain.repository.UserPreferenceRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserPreferenceService {

    private final UserPreferenceRepository preferenceRepository;

    @Transactional(readOnly = true)
    public UserPreference getPreferences(UUID userId) {
        return preferenceRepository.findById(userId)
            .orElseGet(() -> UserPreference.builder().userId(userId).build());
    }

    @Transactional
    public UserPreference savePreferences(UUID userId, String verbosity, String answerFormat,
                                          String defaultProduct, String defaultVersion) {
        UserPreference prefs = preferenceRepository.findById(userId)
            .orElseGet(() -> UserPreference.builder().userId(userId).build());

        if (verbosity != null && isValidVerbosity(verbosity)) prefs.setVerbosity(verbosity);
        if (answerFormat != null && isValidFormat(answerFormat))  prefs.setAnswerFormat(answerFormat);
        if (defaultProduct != null) prefs.setDefaultProduct(defaultProduct.isBlank() ? null : defaultProduct);
        if (defaultVersion != null) prefs.setDefaultVersion(defaultVersion.isBlank() ? null : defaultVersion);

        UserPreference saved = preferenceRepository.save(prefs);
        log.info("Saved preferences for user {}: verbosity={}, format={}", userId, saved.getVerbosity(), saved.getAnswerFormat());
        return saved;
    }

    private static boolean isValidVerbosity(String v) {
        return v.equals("CONCISE") || v.equals("BALANCED") || v.equals("DETAILED");
    }

    private static boolean isValidFormat(String f) {
        return f.equals("PROSE") || f.equals("BULLET_POINTS") || f.equals("CODE_FIRST");
    }
}

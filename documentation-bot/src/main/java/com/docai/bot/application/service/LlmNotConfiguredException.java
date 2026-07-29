package com.docai.bot.application.service;

/**
 * Thrown when an AI feature is invoked for a tenant whose LLM configuration is missing or
 * incomplete (no config row, no API key, unusable provider combination). There is deliberately
 * no platform-level fallback key to degrade to — the tenant's admin must complete the AI settings
 * (Settings → AI Configuration) before AI features work. Mapped to HTTP 422 with code
 * {@code LLM_NOT_CONFIGURED} by GlobalExceptionHandler so the frontend can point admins at the
 * settings page.
 */
public class LlmNotConfiguredException extends RuntimeException {

    public LlmNotConfiguredException(String message) {
        super(message);
    }
}

package com.docai.bot.application.event;

import java.util.UUID;

/**
 * Published once {@link com.docai.bot.application.service.ChatService#processQuery} has built its
 * response, so the two fire-and-forget side writes that reference the (possibly brand-new)
 * {@code ChatSession} row — {@link com.docai.bot.application.service.AnalyticsService#onQueryRecorded}
 * and {@link com.docai.bot.application.service.PeopleAlsoAskedService#onQueryRecorded} — only run
 * after that session is actually committed. Both listeners are {@code @Async}
 * {@code @TransactionalEventListener(phase = AFTER_COMMIT)}: previously they were plain
 * {@code @Async @Transactional} methods called directly from inside the still-open request
 * transaction, which raced it — the async thread could try to insert a row with a foreign key to
 * the session before the session's own INSERT had committed, intermittently failing
 * {@code query_logs_session_id_fkey} / the equivalent on {@code query_session_graph}.
 */
public record ChatQueryRecordedEvent(
    UUID userId,
    UUID tenantId,
    UUID sessionId,
    String question,
    String product,
    String version,
    double confidence,
    long latencyMs,
    int promptTokens,
    int completionTokens,
    String[] citedDocuments,
    double estimatedCostUsd
) {}

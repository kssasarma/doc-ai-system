package com.docai.bot.application.service;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.docai.bot.domain.entity.DataRetentionPolicy;
import com.docai.bot.domain.repository.DataRetentionPolicyRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Runs nightly and purges data older than each tenant's configured retention windows.
 * Covers query logs (query_session_graph), old chat messages, answer feedback, and audit logs.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DataRetentionService {

    private final DataRetentionPolicyRepository policyRepository;
    private final JdbcTemplate jdbc;

    /** Runs every night at 03:00 server time. */
    @Scheduled(cron = "0 0 3 * * *")
    @Transactional
    public void enforceRetentionPolicies() {
        List<DataRetentionPolicy> policies = policyRepository.findAll();
        log.info("Running data retention enforcement for {} tenant policies", policies.size());
        for (DataRetentionPolicy policy : policies) {
            enforceForTenant(policy);
        }
    }

    private void enforceForTenant(DataRetentionPolicy policy) {
        String tenantId = policy.getTenantId().toString();
        LocalDateTime now = LocalDateTime.now();

        // Query / session graph
        int queryRows = jdbc.update(
            "DELETE FROM query_session_graph WHERE tenant_id = ?::uuid AND asked_at < ?",
            tenantId, now.minusDays(policy.getQueryLogDays()));

        // Answer feedback
        int feedbackRows = jdbc.update(
            "DELETE FROM answer_feedback af " +
            "USING chat_messages cm JOIN chat_sessions cs ON cm.chat_id = cs.id " +
            "WHERE af.chat_message_id = cm.id AND cs.tenant_id = ?::uuid " +
            "AND af.created_at < ?",
            tenantId, now.minusDays(policy.getFeedbackDays()));

        // Audit log (keep configured window for compliance)
        int auditRows = jdbc.update(
            "DELETE FROM audit_log WHERE actor_id IN " +
            "(SELECT id FROM users WHERE tenant_id = ?::uuid) " +
            "AND created_at < ?",
            tenantId, now.minusDays(policy.getAuditLogDays()));

        log.info("Retention tenant={} purged: queries={} feedback={} auditLogs={}",
            tenantId, queryRows, feedbackRows, auditRows);
    }
}

package com.docai.ingestor.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.docai.ingestor.application.service.PiiDetectionService;
import com.docai.ingestor.domain.entity.PiiFlag;
import com.docai.ingestor.domain.repository.PiiFlagRepository;

@ExtendWith(MockitoExtension.class)
class PiiDetectionServiceTest {

    @Mock
    private PiiFlagRepository piiFlagRepository;

    private PiiDetectionService piiDetectionService;

    private static final UUID DOC_ID    = UUID.randomUUID();
    private static final UUID TENANT_ID = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        piiDetectionService = new PiiDetectionService(piiFlagRepository);
    }

    // ── No PII ────────────────────────────────────────────────────────────────

    @Test
    void scanAndFlag_cleanContent_returnsFalseAndSavesNothing() {
        boolean critical = piiDetectionService.scanAndFlag(DOC_ID, TENANT_ID,
            "This is a clean document with no personal information.");

        assertThat(critical).isFalse();
        verify(piiFlagRepository, never()).saveAll(anyList());
    }

    // ── Email ─────────────────────────────────────────────────────────────────

    @ParameterizedTest
    @ValueSource(strings = {
        "Contact us at support@example.com for help.",
        "Email user.name+tag@subdomain.example.co.uk",
        "Send reports to info@mycompany.io"
    })
    void scanAndFlag_emailAddress_detectsEmailFlag(String content) {
        ArgumentCaptor<List<PiiFlag>> saved = ArgumentCaptor.forClass(List.class);
        when(piiFlagRepository.saveAll(saved.capture())).thenReturn(List.of());

        boolean critical = piiDetectionService.scanAndFlag(DOC_ID, TENANT_ID, content);

        assertThat(critical).isFalse(); // email is LOW risk
        assertThat(saved.getValue()).anyMatch(f -> "EMAIL".equals(f.getPiiType()));
    }

    // ── SSN ───────────────────────────────────────────────────────────────────

    @Test
    void scanAndFlag_ssnPattern_detectsCriticalFlag() {
        ArgumentCaptor<List<PiiFlag>> saved = ArgumentCaptor.forClass(List.class);
        when(piiFlagRepository.saveAll(saved.capture())).thenReturn(List.of());

        boolean critical = piiDetectionService.scanAndFlag(DOC_ID, TENANT_ID,
            "Employee SSN: 123-45-6789");

        assertThat(critical).isTrue();
        assertThat(saved.getValue()).anyMatch(f ->
            "SSN".equals(f.getPiiType()) && "CRITICAL".equals(f.getRiskLevel()));
    }

    @Test
    void scanAndFlag_multipleSsns_countsAllOccurrences() {
        ArgumentCaptor<List<PiiFlag>> saved = ArgumentCaptor.forClass(List.class);
        when(piiFlagRepository.saveAll(saved.capture())).thenReturn(List.of());

        piiDetectionService.scanAndFlag(DOC_ID, TENANT_ID,
            "SSN1: 123-45-6789 and SSN2: 987-65-4321");

        PiiFlag ssnFlag = saved.getValue().stream()
            .filter(f -> "SSN".equals(f.getPiiType()))
            .findFirst().orElseThrow();
        assertThat(ssnFlag.getOccurrenceCount()).isEqualTo(2);
    }

    // ── Credit Card ───────────────────────────────────────────────────────────

    @ParameterizedTest
    @ValueSource(strings = {
        "Card number 4532015112830366",   // Visa 16-digit
        "Card number 5425233430109903",   // MasterCard
        "Card number 378282246310005"     // Amex 15-digit
    })
    void scanAndFlag_creditCardNumber_detectsCriticalFlag(String content) {
        ArgumentCaptor<List<PiiFlag>> saved = ArgumentCaptor.forClass(List.class);
        when(piiFlagRepository.saveAll(saved.capture())).thenReturn(List.of());

        boolean critical = piiDetectionService.scanAndFlag(DOC_ID, TENANT_ID, content);

        assertThat(critical).isTrue();
        assertThat(saved.getValue()).anyMatch(f ->
            "CREDIT_CARD".equals(f.getPiiType()) && "CRITICAL".equals(f.getRiskLevel()));
    }

    // ── Phone ─────────────────────────────────────────────────────────────────

    @Test
    void scanAndFlag_phoneNumber_detectsMediumRiskFlag() {
        ArgumentCaptor<List<PiiFlag>> saved = ArgumentCaptor.forClass(List.class);
        when(piiFlagRepository.saveAll(saved.capture())).thenReturn(List.of());

        boolean critical = piiDetectionService.scanAndFlag(DOC_ID, TENANT_ID,
            "Call us at 555-867-5309");

        assertThat(critical).isFalse(); // phone is MEDIUM, not CRITICAL
        assertThat(saved.getValue()).anyMatch(f ->
            "PHONE".equals(f.getPiiType()) && "MEDIUM".equals(f.getRiskLevel()));
    }

    // ── IP Address ────────────────────────────────────────────────────────────

    @Test
    void scanAndFlag_ipAddress_detectsLowRiskFlag() {
        ArgumentCaptor<List<PiiFlag>> saved = ArgumentCaptor.forClass(List.class);
        when(piiFlagRepository.saveAll(saved.capture())).thenReturn(List.of());

        boolean critical = piiDetectionService.scanAndFlag(DOC_ID, TENANT_ID,
            "Server IP is 192.168.1.100");

        assertThat(critical).isFalse();
        assertThat(saved.getValue()).anyMatch(f -> "IP_ADDRESS".equals(f.getPiiType()));
    }

    // ── AWS Key ───────────────────────────────────────────────────────────────

    @Test
    void scanAndFlag_awsAccessKey_detectsCriticalFlag() {
        ArgumentCaptor<List<PiiFlag>> saved = ArgumentCaptor.forClass(List.class);
        when(piiFlagRepository.saveAll(saved.capture())).thenReturn(List.of());

        boolean critical = piiDetectionService.scanAndFlag(DOC_ID, TENANT_ID,
            "aws_access_key_id = AKIAIOSFODNN7EXAMPLE");

        assertThat(critical).isTrue();
        assertThat(saved.getValue()).anyMatch(f ->
            "AWS_KEY".equals(f.getPiiType()) && "CRITICAL".equals(f.getRiskLevel()));
    }

    // ── Multiple PII types ────────────────────────────────────────────────────

    @Test
    void scanAndFlag_multipleTypes_flagsAllAndReturnsCritical() {
        ArgumentCaptor<List<PiiFlag>> saved = ArgumentCaptor.forClass(List.class);
        when(piiFlagRepository.saveAll(saved.capture())).thenReturn(List.of());

        boolean critical = piiDetectionService.scanAndFlag(DOC_ID, TENANT_ID,
            "Email: test@test.com SSN: 123-45-6789 Phone: 555-123-4567");

        assertThat(critical).isTrue();
        List<PiiFlag> flags = saved.getValue();
        assertThat(flags.stream().map(PiiFlag::getPiiType))
            .contains("EMAIL", "SSN", "PHONE");
    }

    // ── Tenant and Document IDs ───────────────────────────────────────────────

    @Test
    void scanAndFlag_flagsHaveCorrectDocumentAndTenantIds() {
        ArgumentCaptor<List<PiiFlag>> saved = ArgumentCaptor.forClass(List.class);
        when(piiFlagRepository.saveAll(saved.capture())).thenReturn(List.of());

        piiDetectionService.scanAndFlag(DOC_ID, TENANT_ID, "My SSN is 123-45-6789");

        PiiFlag flag = saved.getValue().get(0);
        assertThat(flag.getDocumentId()).isEqualTo(DOC_ID);
        assertThat(flag.getTenantId()).isEqualTo(TENANT_ID);
    }

    @Test
    void scanAndFlag_sampleExcerptIsRedacted() {
        ArgumentCaptor<List<PiiFlag>> saved = ArgumentCaptor.forClass(List.class);
        when(piiFlagRepository.saveAll(saved.capture())).thenReturn(List.of());

        piiDetectionService.scanAndFlag(DOC_ID, TENANT_ID, "SSN: 123-45-6789");

        String excerpt = saved.getValue().get(0).getSampleExcerpt();
        assertThat(excerpt).doesNotContain("123-45-6789");
        assertThat(excerpt).contains("[SSN redacted]");
    }
}

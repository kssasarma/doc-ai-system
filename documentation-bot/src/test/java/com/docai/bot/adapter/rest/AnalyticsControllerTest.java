package com.docai.bot.adapter.rest;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.docai.bot.application.service.AnalyticsService;
import com.docai.bot.application.service.AnalyticsService.DailyStatDTO;
import com.docai.bot.application.service.AnalyticsService.OverviewDTO;
import com.docai.bot.application.service.ApiKeyService;
import com.docai.bot.application.service.JwtService;
import com.docai.bot.config.GlobalExceptionHandler;
import com.docai.bot.config.SecurityConfig;
import com.docai.bot.config.UserPrincipal;
import com.docai.bot.domain.repository.TenantRepository;
import com.docai.bot.domain.repository.UserRepository;

@WebMvcTest(AnalyticsController.class)
@Import({SecurityConfig.class, GlobalExceptionHandler.class})
class AnalyticsControllerTest {

    @Autowired MockMvc mockMvc;

    @MockitoBean AnalyticsService analyticsService;

    // Filter dependencies
    @MockitoBean JwtService jwtService;
    @MockitoBean ApiKeyService apiKeyService;
    @MockitoBean UserRepository userRepository;
    @MockitoBean TenantRepository tenantRepository;

    private static final UUID USER_ID   = UUID.randomUUID();
    private static final UUID TENANT_ID = UUID.randomUUID();

    // ── GET /api/admin/analytics/overview ─────────────────────────────────────

    @Test
    void getOverview_asAdmin_returns200() throws Exception {
        OverviewDTO overview = buildOverview();
        when(analyticsService.getOverview(TENANT_ID)).thenReturn(overview);

        mockMvc.perform(get("/api/admin/analytics/overview")
                .with(authentication(userAuth("admin", "ADMIN"))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.totalQueriesAllTime").value(100))
            .andExpect(jsonPath("$.avgConfidence").value(0.87));
    }

    @Test
    void getOverview_asUser_returns403() throws Exception {
        mockMvc.perform(get("/api/admin/analytics/overview")
                .with(authentication(userAuth("bob", "USER"))))
            .andExpect(status().isForbidden());
    }

    @Test
    void getOverview_unauthenticated_returns401() throws Exception {
        mockMvc.perform(get("/api/admin/analytics/overview"))
            .andExpect(status().isUnauthorized());
    }

    // ── GET /api/admin/analytics/daily ────────────────────────────────────────

    @Test
    void getDailyStats_asAdmin_returns200WithList() throws Exception {
        DailyStatDTO stat = buildDailyStat("2024-01-01");
        when(analyticsService.getDailyStats(any(), anyInt())).thenReturn(List.of(stat));

        mockMvc.perform(get("/api/admin/analytics/daily")
                .param("days", "7")
                .with(authentication(userAuth("admin", "ADMIN"))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].date").value("2024-01-01"))
            .andExpect(jsonPath("$[0].queryCount").value(42));
    }

    @Test
    void getDailyStats_defaultDays_returns200() throws Exception {
        when(analyticsService.getDailyStats(any(), anyInt())).thenReturn(List.of());

        mockMvc.perform(get("/api/admin/analytics/daily")
                .with(authentication(userAuth("admin", "ADMIN"))))
            .andExpect(status().isOk());
    }

    // ── GET /api/admin/analytics/top-questions ────────────────────────────────

    @Test
    void getTopQuestions_asAdmin_returns200() throws Exception {
        when(analyticsService.getTopQuestions(any(), anyInt(), anyInt())).thenReturn(List.of());

        mockMvc.perform(get("/api/admin/analytics/top-questions")
                .param("limit", "5")
                .param("days", "30")
                .with(authentication(userAuth("admin", "ADMIN"))))
            .andExpect(status().isOk());
    }

    // ── GET /api/admin/analytics/cost ─────────────────────────────────────────

    @Test
    void getCostSummary_asAdmin_returns200() throws Exception {
        when(analyticsService.getCostSummary(any(), anyInt()))
            .thenReturn(AnalyticsService.CostSummaryDTO.builder()
                .totalCostThisMonth(12.50)
                .avgCostPerQuery(0.125)
                .build());

        mockMvc.perform(get("/api/admin/analytics/cost")
                .with(authentication(userAuth("admin", "ADMIN"))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.totalCostThisMonth").value(12.50));
    }

    @Test
    void getCostSummary_asSuperAdmin_returns200() throws Exception {
        when(analyticsService.getCostSummary(any(), anyInt()))
            .thenReturn(AnalyticsService.CostSummaryDTO.builder().build());

        mockMvc.perform(get("/api/admin/analytics/cost")
                .with(authentication(userAuth("superadmin", "SUPER_ADMIN"))))
            .andExpect(status().isOk());
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private static UsernamePasswordAuthenticationToken userAuth(String username, String role) {
        UserPrincipal principal = new UserPrincipal(USER_ID, username, role, TENANT_ID, false);
        return new UsernamePasswordAuthenticationToken(
            principal, null, List.of(new SimpleGrantedAuthority("ROLE_" + role)));
    }

    private static OverviewDTO buildOverview() {
        return OverviewDTO.builder()
            .totalQueriesAllTime(100L)
            .queriesToday(10L)
            .queriesThisWeek(50L)
            .queriesThisMonth(100L)
            .avgConfidence(0.87)
            .totalPositiveFeedback(80L)
            .totalNegativeFeedback(5L)
            .build();
    }

    private static DailyStatDTO buildDailyStat(String date) {
        return DailyStatDTO.builder()
            .date(date)
            .queryCount(42L)
            .avgConfidence(0.9)
            .estimatedCost(1.5)
            .build();
    }
}

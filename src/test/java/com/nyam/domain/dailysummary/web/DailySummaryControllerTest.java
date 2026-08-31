package com.nyam.domain.dailysummary.web;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.nyam.domain.dailysummary.service.DailySummaryResult;
import com.nyam.domain.dailysummary.service.DailySummaryService;
import com.nyam.domain.user.service.AccessTokenIssuer;
import com.nyam.global.security.SecurityConfiguration;
import com.nyam.global.security.SecurityErrorResponder;

/** 실제 Bearer 필터와 공통 오류 계약을 포함해 일별 영양 요약 API를 검증합니다. */
@WebMvcTest(controllers = DailySummaryController.class, properties =
        "NYAM_AUTH_ACCESS_SECRET=QUFBQUFBQUFBQUFBQUFBQUFBQUFBQUFBQUFBQUFBQUE=")
@Import({
        SecurityConfiguration.class,
        SecurityErrorResponder.class,
        AccessTokenIssuer.class,
        DailySummaryControllerTest.FixedClockConfiguration.class
})
class DailySummaryControllerTest {

    private static final Instant NOW = Instant.parse("2026-08-29T00:00:00Z");
    private static final LocalDate DATE = LocalDate.of(2026, 8, 29);

    @Autowired
    MockMvc mockMvc;

    @Autowired
    AccessTokenIssuer accessTokenIssuer;

    @MockitoBean
    DailySummaryService dailySummaryService;

    /** JWT subject와 날짜를 전달하고 영양소별 strict-null 응답을 반환합니다. */
    @Test
    void returnsBearerOwnersDailySummary() throws Exception {
        when(dailySummaryService.get(7L, DATE)).thenReturn(summary(
                2,
                nutrient("30.1234", true),
                nutrient(null, false),
                nutrient("3.0000", true),
                nutrient("1.0000", true)));

        mockMvc.perform(get("/api/v1/daily-summaries")
                        .queryParam("date", "2026-08-29")
                        .header(HttpHeaders.AUTHORIZATION, bearerToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("DAILY_SUMMARY_RETRIEVED"))
                .andExpect(jsonPath("$.data.date").value("2026-08-29"))
                .andExpect(jsonPath("$.data.mealItemCount").value(2))
                .andExpect(jsonPath("$.data.energy.value").value(30.1234))
                .andExpect(jsonPath("$.data.energy.unit").value("kcal"))
                .andExpect(jsonPath("$.data.energy.complete").value(true))
                .andExpect(jsonPath("$.data.carbohydrate.value")
                        .value(org.hamcrest.Matchers.nullValue()))
                .andExpect(jsonPath("$.data.carbohydrate.complete").value(false))
                .andExpect(jsonPath("$.data.userId").doesNotExist());

        verify(dailySummaryService).get(7L, DATE);
    }

    /** 기록이 없어도 요청 date와 결정적인 빈 날짜 성공 결과를 반환합니다. */
    @Test
    void returnsEmptyDateAsSuccess() throws Exception {
        when(dailySummaryService.get(7L, DATE)).thenReturn(summary(
                0,
                nutrient("0", true),
                nutrient("0", true),
                nutrient("0", true),
                nutrient("0", true)));

        mockMvc.perform(get("/api/v1/daily-summaries")
                        .queryParam("date", "2026-08-29")
                        .header(HttpHeaders.AUTHORIZATION, bearerToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.date").value("2026-08-29"))
                .andExpect(jsonPath("$.data.mealItemCount").value(0))
                .andExpect(jsonPath("$.data.energy.value").value(0))
                .andExpect(jsonPath("$.data.energy.complete").value(true));
    }

    /** 날짜 누락과 형식 오류는 서비스를 호출하지 않고 INVALID_INPUT입니다. */
    @Test
    void rejectsMissingAndMalformedDate() throws Exception {
        mockMvc.perform(get("/api/v1/daily-summaries")
                        .header(HttpHeaders.AUTHORIZATION, bearerToken()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"));

        mockMvc.perform(get("/api/v1/daily-summaries")
                        .queryParam("date", "not-a-date")
                        .header(HttpHeaders.AUTHORIZATION, bearerToken()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"));

        verify(dailySummaryService, never()).get(anyLong(), any());
    }

    /** Bearer Token이 없으면 서비스를 호출하지 않고 E003입니다. */
    @Test
    void requiresBearerAuthentication() throws Exception {
        mockMvc.perform(get("/api/v1/daily-summaries").queryParam("date", "2026-08-29"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("E003"));

        verify(dailySummaryService, never()).get(anyLong(), any());
    }

    private DailySummaryResult summary(
            long itemCount,
            DailySummaryResult.Nutrient energy,
            DailySummaryResult.Nutrient carbohydrate,
            DailySummaryResult.Nutrient protein,
            DailySummaryResult.Nutrient fat) {
        return new DailySummaryResult(DATE, itemCount, energy, carbohydrate, protein, fat);
    }

    private DailySummaryResult.Nutrient nutrient(String value, boolean complete) {
        return new DailySummaryResult.Nutrient(value == null ? null : new BigDecimal(value), complete);
    }

    private String bearerToken() {
        return "Bearer " + accessTokenIssuer.issue(7L, NOW);
    }

    /** JWT 테스트용 고정 UTC 시계를 제공합니다. */
    @TestConfiguration
    static class FixedClockConfiguration {

        @Bean
        Clock fixedAuthenticationClock() {
            return Clock.fixed(NOW, ZoneOffset.UTC);
        }
    }
}

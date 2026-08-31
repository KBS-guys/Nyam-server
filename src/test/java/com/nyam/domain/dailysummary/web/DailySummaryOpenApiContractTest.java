package com.nyam.domain.dailysummary.web;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springdoc.core.configuration.SpringDocConfiguration;
import org.springdoc.core.properties.SpringDocConfigProperties;
import org.springdoc.webmvc.core.configuration.SpringDocWebMvcConfiguration;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.nyam.domain.dailysummary.service.DailySummaryService;

/** 한국어 일별 영양 요약 OpenAPI의 인증·입력·strict-null 계약을 검증합니다. */
@WebMvcTest(controllers = DailySummaryController.class, properties = {
        "NYAM_AUTH_ACCESS_SECRET=QUFBQUFBQUFBQUFBQUFBQUFBQUFBQUFBQUFBQUFBQUE=",
        "springdoc.api-docs.enabled=true",
        "springdoc.swagger-ui.enabled=true"
})
@AutoConfigureMockMvc(addFilters = false)
@ImportAutoConfiguration(classes = {
        SpringDocConfiguration.class,
        SpringDocConfigProperties.class,
        SpringDocWebMvcConfiguration.class
})
class DailySummaryOpenApiContractTest {

    @Autowired
    MockMvc mockMvc;

    @MockitoBean
    DailySummaryService dailySummaryService;

    /** GET path, Bearer 요구, 응답 필드와 영양소별 불완전성 설명을 확인합니다. */
    @Test
    void publishesApprovedDailySummaryContract() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paths['/api/v1/daily-summaries'].get.summary")
                        .value("일별 영양 요약 조회"))
                .andExpect(jsonPath("$.paths['/api/v1/daily-summaries'].get.security[0].bearerAuth").exists())
                .andExpect(jsonPath("$.paths['/api/v1/daily-summaries'].get.parameters[0].name")
                        .value("date"))
                .andExpect(jsonPath("$.paths['/api/v1/daily-summaries'].get.parameters[0].required")
                        .value(true))
                .andExpect(jsonPath("$.paths['/api/v1/daily-summaries'].get.description")
                        .value(org.hamcrest.Matchers.allOf(
                                org.hamcrest.Matchers.containsString("snapshot"),
                                org.hamcrest.Matchers.containsString("부분합"),
                                org.hamcrest.Matchers.containsString("mealItemCount"))))
                .andExpect(jsonPath("$.paths['/api/v1/daily-summaries'].get.responses['200'].description")
                        .value(org.hamcrest.Matchers.containsString("DAILY_SUMMARY_RETRIEVED")))
                .andExpect(jsonPath("$.paths['/api/v1/daily-summaries'].get.responses['400']").exists())
                .andExpect(jsonPath("$.paths['/api/v1/daily-summaries'].get.responses['401']").exists())
                .andExpect(jsonPath("$.paths['/api/v1/daily-summaries'].get.responses['500']").exists())
                .andExpect(jsonPath("$.components.schemas.DailySummaryResponse.properties.date").exists())
                .andExpect(jsonPath("$.components.schemas.DailySummaryResponse.properties.mealItemCount").exists())
                .andExpect(jsonPath("$.components.schemas.DailySummaryResponse.properties.energy").exists())
                .andExpect(jsonPath("$.components.schemas.DailySummaryResponse.properties.carbohydrate").exists())
                .andExpect(jsonPath("$.components.schemas.DailySummaryResponse.properties.protein").exists())
                .andExpect(jsonPath("$.components.schemas.DailySummaryResponse.properties.fat").exists())
                .andExpect(jsonPath("$.components.schemas.DailySummaryResponse.properties.userId").doesNotExist())
                .andExpect(jsonPath("$.components.schemas.DailySummaryNutrientResponse.properties.value.type")
                        .value(org.hamcrest.Matchers.hasItem("null")))
                .andExpect(jsonPath("$.components.schemas.DailySummaryNutrientResponse.properties.value.description")
                        .value(org.hamcrest.Matchers.containsString("부분합 대신 null")))
                .andExpect(jsonPath("$.components.schemas.DailySummaryNutrientResponse.properties.complete.description")
                        .value(org.hamcrest.Matchers.containsString("모든 집계 item")));
    }
}

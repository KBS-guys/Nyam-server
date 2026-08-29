package com.nyam.domain.meal.web;

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

import com.nyam.domain.meal.service.MealService;

/**
 * Meal OpenAPI가 한국어 입력·소유권·snapshot과 주요 결과 계약을 게시하는지 검증합니다.
 */
@WebMvcTest(controllers = MealController.class, properties = {
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
class MealOpenApiContractTest {

    @Autowired
    MockMvc mockMvc;

    @MockitoBean
    MealService mealService;

    /** 생성·목록·삭제 경로와 민감 입력 비노출, nullable snapshot 설명을 확인합니다. */
    @Test
    void publishesApprovedMealContract() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paths['/api/v1/meals'].post.summary").value("식사 기록 생성"))
                .andExpect(jsonPath("$.paths['/api/v1/meals'].post.security[0].bearerAuth").exists())
                .andExpect(jsonPath("$.paths['/api/v1/meals'].post.responses['201'].description")
                        .value(org.hamcrest.Matchers.containsString("MEAL_CREATED")))
                .andExpect(jsonPath("$.paths['/api/v1/meals'].post.responses['404'].description")
                        .value(org.hamcrest.Matchers.containsString("FOOD_NOT_FOUND")))
                .andExpect(jsonPath("$.paths['/api/v1/meals'].get.description")
                        .value(org.hamcrest.Matchers.allOf(
                                org.hamcrest.Matchers.containsString("자기 식사"),
                                org.hamcrest.Matchers.containsString("snapshot"))))
                .andExpect(jsonPath("$.paths['/api/v1/meals/{mealId}'].delete.responses['404'].description")
                        .value(org.hamcrest.Matchers.containsString("MEAL_NOT_FOUND")))
                .andExpect(jsonPath("$.components.schemas.CreateMealRequest.properties.mealDate").exists())
                .andExpect(jsonPath("$.components.schemas.CreateMealRequest.properties.items").exists())
                .andExpect(jsonPath("$.components.schemas.CreateMealRequest.properties.userId").doesNotExist())
                .andExpect(jsonPath("$.components.schemas.CreateMealItemRequest.properties.amount.description")
                        .value(org.hamcrest.Matchers.containsString("scale 4")))
                .andExpect(jsonPath("$.components.schemas.MealNutrientSnapshotResponse.properties.value.description")
                        .value(org.hamcrest.Matchers.containsString("0이 아니라 null")));
    }
}

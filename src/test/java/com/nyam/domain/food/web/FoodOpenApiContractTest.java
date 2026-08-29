package com.nyam.domain.food.web;

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

import com.nyam.domain.food.service.FoodQueryService;

/**
 * 식품 OpenAPI가 인증, 단위, null과 공개 오류 계약을 한국어로 설명하는지 검증합니다.
 */
@WebMvcTest(controllers = FoodController.class, properties = {
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
class FoodOpenApiContractTest {

    @Autowired
    MockMvc mockMvc;

    @MockitoBean
    FoodQueryService foodQueryService;

    /**
     * 검색·상세 경로의 Bearer 보안, 응답 코드와 영양 단위 설명을 확인합니다.
     *
     * @throws Exception OpenAPI JSON 조회에 실패한 경우
     */
    @Test
    void publishesApprovedFoodSearchAndDetailContract() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paths['/api/v1/foods/search'].get.summary")
                        .value("식품명 접두사 검색"))
                .andExpect(jsonPath("$.paths['/api/v1/foods/search'].get.security[0].bearerAuth").exists())
                .andExpect(jsonPath("$.paths['/api/v1/foods/search'].get.responses['400'].description")
                        .value(org.hamcrest.Matchers.containsString("INVALID_INPUT")))
                .andExpect(jsonPath("$.paths['/api/v1/foods/search'].get.responses['401'].description")
                        .value(org.hamcrest.Matchers.containsString("E003")))
                .andExpect(jsonPath("$.paths['/api/v1/foods/{foodId}'].get.summary")
                        .value("식품 영양정보 상세 조회"))
                .andExpect(jsonPath("$.paths['/api/v1/foods/{foodId}'].get.description")
                        .value(org.hamcrest.Matchers.allOf(
                                org.hamcrest.Matchers.containsString("기준량"),
                                org.hamcrest.Matchers.containsString("null"))))
                .andExpect(jsonPath("$.paths['/api/v1/foods/{foodId}'].get.responses['404'].description")
                        .value(org.hamcrest.Matchers.containsString("FOOD_NOT_FOUND")))
                .andExpect(jsonPath("$.components.schemas.NutritionBasisResponse.properties.unit.description")
                        .value(org.hamcrest.Matchers.containsString("기준량의 단위")))
                .andExpect(jsonPath("$.components.schemas.NutrientResponse.properties.value.description")
                        .value(org.hamcrest.Matchers.containsString("0과 구분")));
    }
}

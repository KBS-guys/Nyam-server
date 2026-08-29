package com.nyam.domain.food.web;

import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.nyam.domain.food.model.Food;
import com.nyam.domain.food.service.FoodQueryService;
import com.nyam.domain.user.service.AccessTokenIssuer;
import com.nyam.global.exception.BusinessException;
import com.nyam.global.exception.ErrorCode;
import com.nyam.global.security.SecurityConfiguration;
import com.nyam.global.security.SecurityErrorResponder;

/**
 * 실제 Bearer 보안 필터와 공통 오류 계약을 포함해 식품 검색·상세 API를 검증합니다.
 */
@WebMvcTest(controllers = FoodController.class, properties =
        "NYAM_AUTH_ACCESS_SECRET=QUFBQUFBQUFBQUFBQUFBQUFBQUFBQUFBQUFBQUFBQUE=")
@Import({
        SecurityConfiguration.class,
        SecurityErrorResponder.class,
        AccessTokenIssuer.class,
        FoodControllerTest.FixedClockConfiguration.class
})
class FoodControllerTest {

    private static final Instant NOW = Instant.parse("2026-08-27T00:00:00Z");

    @Autowired
    MockMvc mockMvc;

    @Autowired
    AccessTokenIssuer accessTokenIssuer;

    @MockitoBean
    FoodQueryService foodQueryService;

    /**
     * 인증된 검색이 외부 식품 코드를 제외하고 기준량과 소문자 단위를 반환하는지 확인합니다.
     *
     * @throws Exception MockMvc 요청 처리에 실패한 경우
     */
    @Test
    void authenticatedSearchReturnsBoundedResponseShape() throws Exception {
        Food food = food(11L, "국밥_돼지머리", new BigDecimal("100.0000"), "G", null);
        when(foodQueryService.search("국밥")).thenReturn(List.of(food));

        mockMvc.perform(get("/api/v1/foods/search")
                        .queryParam("query", "국밥")
                        .header(HttpHeaders.AUTHORIZATION, bearerToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("S000"))
                .andExpect(jsonPath("$.data[0].foodId").value(11))
                .andExpect(jsonPath("$.data[0].name").value("국밥_돼지머리"))
                .andExpect(jsonPath("$.data[0].nutritionBasis.amount").value(100))
                .andExpect(jsonPath("$.data[0].nutritionBasis.unit").value("g"))
                .andExpect(jsonPath("$.data[0].sourceFoodCode").doesNotExist());
    }

    /**
     * 상세 응답이 누락 영양값을 숫자 0이 아닌 null로 두고 단위를 유지하는지 확인합니다.
     *
     * @throws Exception MockMvc 요청 처리에 실패한 경우
     */
    @Test
    void detailPreservesMissingNutrientAsNullWithUnit() throws Exception {
        Food food = food(12L, "테스트 식품", new BigDecimal("100.0000"), "ML", null);
        when(foodQueryService.get(12L)).thenReturn(food);

        mockMvc.perform(get("/api/v1/foods/12")
                        .header(HttpHeaders.AUTHORIZATION, bearerToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.nutritionBasis.unit").value("ml"))
                .andExpect(jsonPath("$.data.energy.value").value(org.hamcrest.Matchers.nullValue()))
                .andExpect(jsonPath("$.data.energy.unit").value("kcal"))
                .andExpect(jsonPath("$.data.carbohydrate.value").value(15.5))
                .andExpect(jsonPath("$.data.carbohydrate.unit").value("g"));
    }

    /**
     * Bearer Token이 없으면 서비스 호출 전에 기존 E003 계약으로 거절하는지 확인합니다.
     *
     * @throws Exception MockMvc 요청 처리에 실패한 경우
     */
    @Test
    void requiresBearerAuthentication() throws Exception {
        mockMvc.perform(get("/api/v1/foods/search").queryParam("query", "국밥"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("E003"));

        verify(foodQueryService, never()).search(anyString());
    }

    /**
     * 숫자가 아닌 식품 ID를 안전한 공통 입력 오류로 변환하는지 확인합니다.
     *
     * @throws Exception MockMvc 요청 처리에 실패한 경우
     */
    @Test
    void rejectsNonNumericFoodIdAsInvalidInput() throws Exception {
        mockMvc.perform(get("/api/v1/foods/not-a-number")
                        .header(HttpHeaders.AUTHORIZATION, bearerToken()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"));

        verify(foodQueryService, never()).get(anyLong());
    }

    /**
     * 존재하지 않는 양의 식품 ID를 FOOD_NOT_FOUND 404로 반환하는지 확인합니다.
     *
     * @throws Exception MockMvc 요청 처리에 실패한 경우
     */
    @Test
    void returnsSafeNotFoundResponse() throws Exception {
        when(foodQueryService.get(999L)).thenThrow(new BusinessException(ErrorCode.FOOD_NOT_FOUND));

        mockMvc.perform(get("/api/v1/foods/999")
                        .header(HttpHeaders.AUTHORIZATION, bearerToken()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("FOOD_NOT_FOUND"))
                .andExpect(jsonPath("$.message").value("식품을 찾을 수 없습니다."));
    }

    /**
     * 응답 매핑에 필요한 Food getter만 제공하는 테스트 대역을 구성합니다.
     *
     * @param id 식품 식별자
     * @param name 식품명
     * @param basisAmount 기준량
     * @param basisUnit 기준 단위
     * @param energy 에너지 값
     * @return 식품 엔티티 테스트 대역
     */
    private Food food(Long id, String name, BigDecimal basisAmount, String basisUnit, BigDecimal energy) {
        Food food = org.mockito.Mockito.mock(Food.class);
        when(food.getId()).thenReturn(id);
        when(food.getFoodName()).thenReturn(name);
        when(food.getBasisAmount()).thenReturn(basisAmount);
        when(food.getBasisUnit()).thenReturn(basisUnit);
        when(food.getEnergy()).thenReturn(energy);
        when(food.getCarbohydrate()).thenReturn(new BigDecimal("15.5000"));
        when(food.getProtein()).thenReturn(new BigDecimal("6.7000"));
        when(food.getFat()).thenReturn(new BigDecimal("5.1600"));
        return food;
    }

    /**
     * 테스트용 유효 Bearer Access Token을 발급합니다.
     *
     * @return Authorization 헤더 값
     */
    private String bearerToken() {
        return "Bearer " + accessTokenIssuer.issue(7L, NOW);
    }

    /**
     * 식품 웹 테스트의 JWT 발급과 검증 시간을 고정합니다.
     */
    @TestConfiguration
    static class FixedClockConfiguration {

        /**
         * 운영 UTC 시계보다 우선하는 고정 테스트 시계를 제공합니다.
         *
         * @return 고정 UTC 시계
         */
        @Bean
        @Primary
        Clock fixedFoodClock() {
            return Clock.fixed(NOW, ZoneOffset.UTC);
        }
    }
}

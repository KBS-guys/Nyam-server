package com.nyam.domain.meal.web;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;

import com.nyam.domain.meal.model.Meal;
import com.nyam.domain.meal.model.MealItem;
import com.nyam.domain.meal.service.CreateMealCommand;
import com.nyam.domain.meal.service.MealService;
import com.nyam.domain.user.service.AccessTokenIssuer;
import com.nyam.global.exception.BusinessException;
import com.nyam.global.exception.ErrorCode;
import com.nyam.global.security.SecurityConfiguration;
import com.nyam.global.security.SecurityErrorResponder;

/**
 * 실제 Bearer 보안 필터와 공통 오류 계약을 포함해 Meal HTTP API를 검증합니다.
 */
@WebMvcTest(controllers = MealController.class, properties =
        "NYAM_AUTH_ACCESS_SECRET=QUFBQUFBQUFBQUFBQUFBQUFBQUFBQUFBQUFBQUFBQUE=")
@Import({
        SecurityConfiguration.class,
        SecurityErrorResponder.class,
        AccessTokenIssuer.class,
        MealControllerTest.FixedClockConfiguration.class
})
class MealControllerTest {

    private static final Instant NOW = Instant.parse("2026-08-29T00:00:00Z");

    @Autowired
    MockMvc mockMvc;

    @Autowired
    AccessTokenIssuer accessTokenIssuer;

    @MockitoBean
    MealService mealService;

    /** 생성 요청이 JWT subject만 소유자로 사용하고 저장 snapshot을 201로 반환하는지 확인합니다. */
    @Test
    void createsMealFromBearerOwner() throws Exception {
        when(mealService.create(eq(7L), any(CreateMealCommand.class))).thenReturn(meal(31L));

        mockMvc.perform(post("/api/v1/meals")
                        .header(HttpHeaders.AUTHORIZATION, bearerToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "mealDate": "2026-08-29",
                                  "items": [{"foodId": 11, "amount": 150.00000}],
                                  "userId": 999,
                                  "energy": 999999
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.code").value("MEAL_CREATED"))
                .andExpect(jsonPath("$.data.mealId").value(31))
                .andExpect(jsonPath("$.data.mealDate").value("2026-08-29"))
                .andExpect(jsonPath("$.data.items[0].foodId").value(11))
                .andExpect(jsonPath("$.data.items[0].name").value("현미밥"))
                .andExpect(jsonPath("$.data.items[0].unit").value("g"))
                .andExpect(jsonPath("$.data.items[0].energy.value").value(185.1851))
                .andExpect(jsonPath("$.data.items[0].carbohydrate.value").value(org.hamcrest.Matchers.nullValue()))
                .andExpect(jsonPath("$.data.items[0].energy.unit").value("kcal"));

        verify(mealService).create(eq(7L), any(CreateMealCommand.class));
    }

    /** 빈 item 요청은 서비스 호출 전에 INVALID_INPUT으로 거절하는지 확인합니다. */
    @Test
    void rejectsInvalidCreateBody() throws Exception {
        mockMvc.perform(post("/api/v1/meals")
                        .header(HttpHeaders.AUTHORIZATION, bearerToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"mealDate":"2026-08-29","items":[]}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"));

        verify(mealService, never()).create(anyLong(), any());
    }

    /** 날짜별 목록이 JWT 소유자와 요청 날짜를 서비스에 전달하는지 확인합니다. */
    @Test
    void listsOnlyBearerOwnersDate() throws Exception {
        when(mealService.list(7L, LocalDate.of(2026, 8, 29))).thenReturn(List.of(meal(31L)));

        mockMvc.perform(get("/api/v1/meals")
                        .queryParam("date", "2026-08-29")
                        .header(HttpHeaders.AUTHORIZATION, bearerToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("MEALS_RETRIEVED"))
                .andExpect(jsonPath("$.data[0].mealId").value(31));

        verify(mealService).list(7L, LocalDate.of(2026, 8, 29));
    }

    /** 기록이 없는 날짜도 빈 목록과 승인된 성공 코드로 반환하는지 확인합니다. */
    @Test
    void returnsEmptyDateListAsSuccess() throws Exception {
        when(mealService.list(7L, LocalDate.of(2026, 8, 30))).thenReturn(List.of());

        mockMvc.perform(get("/api/v1/meals")
                        .queryParam("date", "2026-08-30")
                        .header(HttpHeaders.AUTHORIZATION, bearerToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("MEALS_RETRIEVED"))
                .andExpect(jsonPath("$.data").isEmpty());
    }

    /** 소유 식사 삭제가 item 삭제를 서비스에 위임하고 승인된 성공 코드를 반환하는지 확인합니다. */
    @Test
    void deletesOwnedMeal() throws Exception {
        mockMvc.perform(delete("/api/v1/meals/31")
                        .header(HttpHeaders.AUTHORIZATION, bearerToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("MEAL_DELETED"))
                .andExpect(jsonPath("$.data").value(org.hamcrest.Matchers.nullValue()));

        verify(mealService).delete(7L, 31L);
    }

    /** 다른 사용자 소유와 없는 식사 삭제가 동일한 404 계약으로 노출되는지 확인합니다. */
    @Test
    void hidesNonOwnedMealDelete() throws Exception {
        when(mealService.list(anyLong(), any())).thenReturn(List.of());
        org.mockito.Mockito.doThrow(new BusinessException(ErrorCode.MEAL_NOT_FOUND))
                .when(mealService).delete(7L, 99L);

        mockMvc.perform(delete("/api/v1/meals/99")
                        .header(HttpHeaders.AUTHORIZATION, bearerToken()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("MEAL_NOT_FOUND"));
    }

    /** 숫자가 아닌 meal ID를 서비스 호출 없이 공통 입력 오류로 변환하는지 확인합니다. */
    @Test
    void rejectsNonNumericMealId() throws Exception {
        mockMvc.perform(delete("/api/v1/meals/not-a-number")
                        .header(HttpHeaders.AUTHORIZATION, bearerToken()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"));

        verify(mealService, never()).delete(anyLong(), anyLong());
    }

    /** Bearer Token이 없으면 Meal 서비스를 호출하지 않고 E003으로 거절하는지 확인합니다. */
    @Test
    void requiresBearerAuthentication() throws Exception {
        mockMvc.perform(get("/api/v1/meals").queryParam("date", "2026-08-29"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("E003"));

        verify(mealService, never()).list(anyLong(), any());
    }

    private Meal meal(long mealId) {
        Meal meal = new Meal(7L, LocalDate.of(2026, 8, 29));
        meal.addItem(new MealItem(
                1, 11L, "현미밥", new BigDecimal("150.0000"), "G",
                new BigDecimal("185.1851"), "KCAL",
                null, "G",
                new BigDecimal("5.0000"), "G",
                new BigDecimal("1.6667"), "G"));
        ReflectionTestUtils.setField(meal, "id", mealId);
        return meal;
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

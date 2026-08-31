package com.nyam.domain.dailysummary.web;

import java.math.BigDecimal;
import java.time.LocalDate;

import com.nyam.domain.dailysummary.service.DailySummaryResult;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.annotation.Nullable;

/**
 * 요청 날짜의 meal item snapshot 주요 영양 합계와 불완전성을 공개합니다.
 */
@Schema(description = "요청 날짜의 저장된 meal item snapshot 일별 영양 요약")
public record DailySummaryResponse(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "2026-08-29",
                description = "요청한 식사 기준 날짜")
        LocalDate date,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "3",
                description = "현재 사용자와 요청 날짜에 포함된 meal item 수")
        long mealItemCount,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) Nutrient energy,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) Nutrient carbohydrate,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) Nutrient protein,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) Nutrient fat) {

    /** 서비스의 strict-null 판정을 공개 단위와 함께 응답으로 변환합니다. */
    public static DailySummaryResponse from(DailySummaryResult result) {
        return new DailySummaryResponse(
                result.date(),
                result.mealItemCount(),
                Nutrient.from(result.energy(), "kcal"),
                Nutrient.from(result.carbohydrate(), "g"),
                Nutrient.from(result.protein(), "g"),
                Nutrient.from(result.fat(), "g"));
    }

    /** nullable 합계, 고정 단위와 모든 item의 값 존재 여부입니다. */
    @Schema(name = "DailySummaryNutrientResponse",
            description = "영양소별 strict-null 합계와 모든 item의 값 존재 여부")
    public record Nutrient(
            @Nullable
            @Schema(nullable = true,
                    description = "하나라도 값이 누락되면 부분합 대신 null이며 빈 날짜의 합은 0")
            BigDecimal value,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED, allowableValues = {"kcal", "g"})
            String unit,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED,
                    description = "모든 집계 item에 해당 영양값이 존재하면 true")
            boolean complete) {

        private static Nutrient from(DailySummaryResult.Nutrient nutrient, String unit) {
            return new Nutrient(nutrient.value(), unit, nutrient.complete());
        }
    }
}

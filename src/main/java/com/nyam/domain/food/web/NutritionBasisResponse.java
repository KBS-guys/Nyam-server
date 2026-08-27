package com.nyam.domain.food.web;

import java.math.BigDecimal;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 영양값이 어떤 식품 기준량을 대상으로 하는지 설명합니다.
 *
 * @param amount 영양정보 기준량
 * @param unit 기준 단위인 {@code g} 또는 {@code ml}
 */
@Schema(description = "식품 영양정보의 기준량과 기준 단위")
public record NutritionBasisResponse(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "100",
                description = "모든 영양값이 적용되는 기준량입니다.")
        BigDecimal amount,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, allowableValues = {"g", "ml"},
                description = "기준량의 단위입니다.")
        String unit) {
}

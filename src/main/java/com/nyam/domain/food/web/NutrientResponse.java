package com.nyam.domain.food.web;

import java.math.BigDecimal;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.annotation.Nullable;

/**
 * 한 영양소의 값과 명시적 단위를 함께 전달합니다.
 *
 * @param value 원천에 값이 없으면 {@code null}인 영양값
 * @param unit 영양값 단위
 */
@Schema(description = "영양소 값과 단위. 원천 값이 없으면 value만 null입니다.")
public record NutrientResponse(
        @Nullable
        @Schema(nullable = true, description = "영양소 값입니다. 원천 값이 없으면 null이며 0과 구분됩니다.")
        BigDecimal value,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, allowableValues = {"kcal", "g"},
                description = "영양소 단위입니다.")
        String unit) {
}

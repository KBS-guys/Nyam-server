package com.nyam.domain.dailysummary.service;

import java.math.BigDecimal;
import java.time.LocalDate;

import jakarta.annotation.Nullable;

/**
 * 저장된 meal item snapshot의 일별 합계와 영양소별 완전성 판정입니다.
 */
public record DailySummaryResult(
        LocalDate date,
        long mealItemCount,
        Nutrient energy,
        Nutrient carbohydrate,
        Nutrient protein,
        Nutrient fat) {

    /** 알려진 모든 값의 합계 또는 누락을 공개하는 영양소별 결과입니다. */
    public record Nutrient(@Nullable BigDecimal value, boolean complete) {
    }
}

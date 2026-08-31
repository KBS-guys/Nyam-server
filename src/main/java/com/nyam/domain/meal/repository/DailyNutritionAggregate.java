package com.nyam.domain.meal.repository;

import java.math.BigDecimal;

import jakarta.annotation.Nullable;

/**
 * 한 사용자의 한 날짜 meal item snapshot 집계 결과를 전달하는 내부 조회 projection입니다.
 */
public interface DailyNutritionAggregate {

    Long getMealItemCount();

    Long getEnergyCount();

    @Nullable
    BigDecimal getEnergySum();

    Long getCarbohydrateCount();

    @Nullable
    BigDecimal getCarbohydrateSum();

    Long getProteinCount();

    @Nullable
    BigDecimal getProteinSum();

    Long getFatCount();

    @Nullable
    BigDecimal getFatSum();
}

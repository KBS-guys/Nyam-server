package com.nyam.domain.dailysummary.service;

import java.math.BigDecimal;
import java.time.LocalDate;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.nyam.domain.meal.repository.DailyNutritionAggregate;
import com.nyam.domain.meal.repository.MealRepository;
import com.nyam.domain.meal.service.MealDatePolicy;
import com.nyam.global.exception.BusinessException;
import com.nyam.global.exception.ErrorCode;

/**
 * 인증 사용자의 지정 날짜 meal item snapshot을 집계하고 strict-null 정책을 적용합니다.
 */
@Service
public class DailySummaryService {

    private final MealRepository mealRepository;

    public DailySummaryService(MealRepository mealRepository) {
        this.mealRepository = mealRepository;
    }

    /**
     * 소유자와 날짜가 일치하는 snapshot만 한 query로 집계합니다.
     *
     * @param userId JWT subject에서 얻은 내부 사용자 식별자
     * @param date 사용자가 요청한 식사 기준 날짜
     * @return 영양소별 strict-null 판정이 적용된 일별 요약
     * @throws BusinessException 사용자 또는 날짜 입력이 유효하지 않은 경우
     */
    @Transactional(readOnly = true)
    public DailySummaryResult get(long userId, LocalDate date) {
        if (userId <= 0) {
            throw new BusinessException(ErrorCode.INVALID_INPUT);
        }
        MealDatePolicy.requireValid(date);

        DailyNutritionAggregate aggregate = mealRepository.aggregateDailyNutrition(userId, date);
        if (aggregate == null) {
            throw invalidAggregate();
        }

        long itemCount = requireNonNegative(aggregate.getMealItemCount());
        return new DailySummaryResult(
                date,
                itemCount,
                nutrient(itemCount, aggregate.getEnergyCount(), aggregate.getEnergySum()),
                nutrient(itemCount, aggregate.getCarbohydrateCount(), aggregate.getCarbohydrateSum()),
                nutrient(itemCount, aggregate.getProteinCount(), aggregate.getProteinSum()),
                nutrient(itemCount, aggregate.getFatCount(), aggregate.getFatSum()));
    }

    private DailySummaryResult.Nutrient nutrient(long itemCount, Long knownCountValue, BigDecimal sum) {
        long knownCount = requireNonNegative(knownCountValue);
        if (knownCount > itemCount
                || knownCount == 0 && sum != null
                || knownCount > 0 && (sum == null || sum.signum() < 0)) {
            throw invalidAggregate();
        }
        if (itemCount == 0) {
            return new DailySummaryResult.Nutrient(BigDecimal.ZERO, true);
        }
        if (knownCount < itemCount) {
            return new DailySummaryResult.Nutrient(null, false);
        }
        return new DailySummaryResult.Nutrient(sum, true);
    }

    private long requireNonNegative(Long count) {
        if (count == null || count < 0) {
            throw invalidAggregate();
        }
        return count;
    }

    private IllegalStateException invalidAggregate() {
        return new IllegalStateException("Invalid daily nutrition aggregate");
    }
}

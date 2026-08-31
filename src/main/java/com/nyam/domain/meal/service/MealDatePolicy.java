package com.nyam.domain.meal.service;

import java.time.LocalDate;

import com.nyam.global.exception.BusinessException;
import com.nyam.global.exception.ErrorCode;

/**
 * meal과 그 집계 기능이 공유하는 MySQL {@code DATE} 범위 정책입니다.
 */
public final class MealDatePolicy {

    private static final LocalDate MIN_DATE = LocalDate.of(1000, 1, 1);
    private static final LocalDate MAX_DATE = LocalDate.of(9999, 12, 31);

    private MealDatePolicy() {
    }

    /**
     * 식사 기준 날짜가 MySQL {@code DATE} 범위인지 확인합니다.
     *
     * @param mealDate 사용자가 지정한 식사 기준 날짜
     * @throws BusinessException 날짜가 없거나 허용 범위를 벗어난 경우
     */
    public static void requireValid(LocalDate mealDate) {
        if (mealDate == null || mealDate.isBefore(MIN_DATE) || mealDate.isAfter(MAX_DATE)) {
            throw new BusinessException(ErrorCode.INVALID_INPUT);
        }
    }
}

package com.nyam.domain.meal.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * HTTP 표현과 분리된 식사 생성 입력입니다.
 *
 * @param mealDate 사용자가 지정한 식사 기준 날짜
 * @param items food 식별자와 섭취량 목록
 */
public record CreateMealCommand(LocalDate mealDate, List<Item> items) {

    /**
     * 한 food의 섭취량 입력입니다.
     *
     * @param foodId food 식별자
     * @param amount food 기준 단위로 표현한 섭취량
     */
    public record Item(Long foodId, BigDecimal amount) {
    }
}

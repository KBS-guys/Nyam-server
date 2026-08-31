package com.nyam.domain.dailysummary.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.nyam.domain.meal.repository.DailyNutritionAggregate;
import com.nyam.domain.meal.repository.MealRepository;
import com.nyam.global.exception.BusinessException;
import com.nyam.global.exception.ErrorCode;

/** 영양소별 strict-null 판정과 집계 projection 방어 규칙을 검증합니다. */
class DailySummaryServiceTest {

    private static final LocalDate DATE = LocalDate.of(2026, 8, 29);

    private MealRepository mealRepository;
    private DailySummaryService dailySummaryService;

    @BeforeEach
    void setUp() {
        mealRepository = mock(MealRepository.class);
        dailySummaryService = new DailySummaryService(mealRepository);
    }

    /** 모든 item에 값이 있으면 합계를 추가 반올림 없이 반환합니다. */
    @Test
    void returnsCompleteSumsWithoutAdditionalRounding() {
        when(mealRepository.aggregateDailyNutrition(7L, DATE)).thenReturn(aggregate(
                2L,
                2L, "30.1234",
                2L, "12.0000",
                2L, "3.0000",
                2L, "1.0000"));

        DailySummaryResult result = dailySummaryService.get(7L, DATE);

        assertThat(result.mealItemCount()).isEqualTo(2);
        assertThat(result.energy().value()).isEqualByComparingTo("30.1234");
        assertThat(result.energy().complete()).isTrue();
        assertThat(result.carbohydrate().value()).isEqualByComparingTo("12.0000");
    }

    /** 한 영양소의 누락은 그 영양소의 부분합만 숨기고 다른 합계에는 영향을 주지 않습니다. */
    @Test
    void appliesStrictNullPerNutrient() {
        when(mealRepository.aggregateDailyNutrition(7L, DATE)).thenReturn(aggregate(
                2L,
                1L, "10.0000",
                2L, "12.0000",
                0L, null,
                2L, "1.0000"));

        DailySummaryResult result = dailySummaryService.get(7L, DATE);

        assertThat(result.energy().value()).isNull();
        assertThat(result.energy().complete()).isFalse();
        assertThat(result.carbohydrate().value()).isEqualByComparingTo("12.0000");
        assertThat(result.carbohydrate().complete()).isTrue();
        assertThat(result.protein().value()).isNull();
        assertThat(result.protein().complete()).isFalse();
    }

    /** 빈 날짜와 item이 있지만 실제 합계가 0인 날짜를 item 수로 구분합니다. */
    @Test
    void distinguishesEmptyDateFromRecordedZero() {
        when(mealRepository.aggregateDailyNutrition(7L, DATE)).thenReturn(aggregate(
                0L, 0L, null, 0L, null, 0L, null, 0L, null));
        DailySummaryResult empty = dailySummaryService.get(7L, DATE);

        assertThat(empty.mealItemCount()).isZero();
        assertThat(empty.energy().value()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(empty.energy().complete()).isTrue();

        LocalDate zeroDate = DATE.plusDays(1);
        when(mealRepository.aggregateDailyNutrition(7L, zeroDate)).thenReturn(aggregate(
                1L, 1L, "0.0000", 1L, "0.0000", 1L, "0.0000", 1L, "0.0000"));
        DailySummaryResult recordedZero = dailySummaryService.get(7L, zeroDate);

        assertThat(recordedZero.mealItemCount()).isOne();
        assertThat(recordedZero.energy().value()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(recordedZero.energy().complete()).isTrue();
    }

    /** projection 자체나 count·sum 불변식 위반을 공개값으로 보정하지 않습니다. */
    @Test
    void rejectsInvalidAggregateProjection() {
        when(mealRepository.aggregateDailyNutrition(7L, DATE)).thenReturn(null);
        assertThatThrownBy(() -> dailySummaryService.get(7L, DATE))
                .isInstanceOf(IllegalStateException.class);

        when(mealRepository.aggregateDailyNutrition(7L, DATE)).thenReturn(aggregate(
                1L, 2L, "10.0000", 1L, "1.0000", 1L, "1.0000", 1L, "1.0000"));
        assertThatThrownBy(() -> dailySummaryService.get(7L, DATE))
                .isInstanceOf(IllegalStateException.class);

        when(mealRepository.aggregateDailyNutrition(7L, DATE)).thenReturn(aggregate(
                1L, 0L, "10.0000", 1L, "1.0000", 1L, "1.0000", 1L, "1.0000"));
        assertThatThrownBy(() -> dailySummaryService.get(7L, DATE))
                .isInstanceOf(IllegalStateException.class);
    }

    /** 사용자와 meal 날짜 정책 위반은 공통 입력 오류입니다. */
    @Test
    void rejectsInvalidOwnerAndDate() {
        assertThatThrownBy(() -> dailySummaryService.get(0L, DATE))
                .isInstanceOfSatisfying(BusinessException.class,
                        exception -> assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.INVALID_INPUT));
        assertThatThrownBy(() -> dailySummaryService.get(7L, LocalDate.of(999, 12, 31)))
                .isInstanceOfSatisfying(BusinessException.class,
                        exception -> assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.INVALID_INPUT));
    }

    private DailyNutritionAggregate aggregate(
            Long itemCount,
            Long energyCount,
            String energySum,
            Long carbohydrateCount,
            String carbohydrateSum,
            Long proteinCount,
            String proteinSum,
            Long fatCount,
            String fatSum) {
        return new TestAggregate(
                itemCount,
                energyCount,
                decimal(energySum),
                carbohydrateCount,
                decimal(carbohydrateSum),
                proteinCount,
                decimal(proteinSum),
                fatCount,
                decimal(fatSum));
    }

    private BigDecimal decimal(String value) {
        return value == null ? null : new BigDecimal(value);
    }

    private record TestAggregate(
            Long mealItemCount,
            Long energyCount,
            BigDecimal energySum,
            Long carbohydrateCount,
            BigDecimal carbohydrateSum,
            Long proteinCount,
            BigDecimal proteinSum,
            Long fatCount,
            BigDecimal fatSum) implements DailyNutritionAggregate {

        @Override
        public Long getMealItemCount() {
            return mealItemCount;
        }

        @Override
        public Long getEnergyCount() {
            return energyCount;
        }

        @Override
        public BigDecimal getEnergySum() {
            return energySum;
        }

        @Override
        public Long getCarbohydrateCount() {
            return carbohydrateCount;
        }

        @Override
        public BigDecimal getCarbohydrateSum() {
            return carbohydrateSum;
        }

        @Override
        public Long getProteinCount() {
            return proteinCount;
        }

        @Override
        public BigDecimal getProteinSum() {
            return proteinSum;
        }

        @Override
        public Long getFatCount() {
            return fatCount;
        }

        @Override
        public BigDecimal getFatSum() {
            return fatSum;
        }
    }
}

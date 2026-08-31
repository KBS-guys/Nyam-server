package com.nyam.domain.meal.repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.nyam.domain.meal.model.Meal;

/**
 * 인증 소유자 조건을 포함한 식사 저장과 날짜별 nested 조회를 담당합니다.
 */
public interface MealRepository extends JpaRepository<Meal, Long> {

    /**
     * 소유자와 날짜가 일치하는 meal과 item을 meal 식별자 내림차순으로 조회합니다.
     */
    @EntityGraph(attributePaths = "items")
    List<Meal> findByUserIdAndMealDateOrderByIdDesc(Long userId, LocalDate mealDate);

    /**
     * 소유자와 날짜가 일치하는 item 수, 영양소별 값 존재 수와 nullable 합계를 한 번에 조회합니다.
     */
    @Query("""
            select count(item.id) as mealItemCount,
                   count(item.energySnapshot) as energyCount,
                   sum(item.energySnapshot) as energySum,
                   count(item.carbohydrateSnapshot) as carbohydrateCount,
                   sum(item.carbohydrateSnapshot) as carbohydrateSum,
                   count(item.proteinSnapshot) as proteinCount,
                   sum(item.proteinSnapshot) as proteinSum,
                   count(item.fatSnapshot) as fatCount,
                   sum(item.fatSnapshot) as fatSum
            from Meal meal
            join meal.items item
            where meal.userId = :userId and meal.mealDate = :mealDate
            """)
    DailyNutritionAggregate aggregateDailyNutrition(
            @Param("userId") Long userId,
            @Param("mealDate") LocalDate mealDate);

    /**
     * 삭제 대상의 존재와 소유권을 한 조건으로 확인합니다.
     */
    Optional<Meal> findByIdAndUserId(Long mealId, Long userId);
}

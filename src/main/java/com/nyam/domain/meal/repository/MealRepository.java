package com.nyam.domain.meal.repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

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
     * 삭제 대상의 존재와 소유권을 한 조건으로 확인합니다.
     */
    Optional<Meal> findByIdAndUserId(Long mealId, Long userId);
}

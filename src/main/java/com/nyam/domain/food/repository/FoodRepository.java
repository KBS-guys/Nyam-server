package com.nyam.domain.food.repository;

import java.util.List;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.nyam.domain.food.model.Food;

/**
 * 적재된 식품의 읽기 전용 검색과 상세 조회를 담당합니다.
 */
public interface FoodRepository extends JpaRepository<Food, Long> {

    /**
     * 이미 escape된 정규화 접두사와 일치하는 식품을 결정적 순서로 조회합니다.
     *
     * @param escapedPrefix LIKE 특수문자가 리터럴 처리된 정규화 접두사
     * @param pageable 최대 결과 수를 제한하는 페이지 정보
     * @return 정규화 이름과 외부 코드 순으로 정렬된 식품 목록
     */
    @Query("""
            SELECT food
            FROM Food food
            WHERE food.normalizedName LIKE CONCAT(:escapedPrefix, '%') ESCAPE '!'
            ORDER BY food.normalizedName ASC, food.sourceFoodCode ASC
            """)
    List<Food> findByNormalizedPrefix(
            @Param("escapedPrefix") String escapedPrefix,
            Pageable pageable);
}

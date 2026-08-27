package com.nyam.domain.food.web;

import com.nyam.domain.food.model.Food;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 식품명 접두사 검색에서 반환할 최소 식품 정보입니다.
 *
 * @param foodId 내부 식품 식별자
 * @param name 사용자에게 표시할 식품명
 * @param nutritionBasis 영양정보 기준량과 단위
 */
@Schema(description = "식품명 접두사 검색 결과")
public record FoodSearchResponse(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "1", description = "식품 상세 조회에 사용할 식별자")
        Long foodId,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "국밥_돼지머리", description = "공공 원천 식품명")
        String name,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "영양정보 기준량과 기준 단위")
        NutritionBasisResponse nutritionBasis) {

    /**
     * JPA 엔티티를 외부 검색 응답 DTO로 변환합니다.
     *
     * @param food 조회된 식품 엔티티
     * @return 외부 식품 코드를 제외한 검색 응답
     */
    public static FoodSearchResponse from(Food food) {
        return new FoodSearchResponse(
                food.getId(),
                food.getFoodName(),
                new NutritionBasisResponse(food.getBasisAmount(), food.getBasisUnit().toLowerCase()));
    }
}

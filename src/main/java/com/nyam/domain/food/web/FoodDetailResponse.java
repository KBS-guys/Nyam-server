package com.nyam.domain.food.web;

import com.nyam.domain.food.model.Food;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 한 식품의 기준량과 주요 영양값을 반환합니다.
 *
 * @param foodId 내부 식품 식별자
 * @param name 사용자에게 표시할 식품명
 * @param nutritionBasis 영양정보 기준량과 단위
 * @param energy 에너지 값과 단위
 * @param carbohydrate 탄수화물 값과 단위
 * @param protein 단백질 값과 단위
 * @param fat 지방 값과 단위
 */
@Schema(description = "식품의 영양 기준과 주요 영양소 상세")
public record FoodDetailResponse(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "1", description = "식품 식별자")
        Long foodId,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "국밥_돼지머리", description = "공공 원천 식품명")
        String name,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "영양정보 기준량과 기준 단위")
        NutritionBasisResponse nutritionBasis,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "에너지 값과 kcal 단위")
        NutrientResponse energy,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "탄수화물 값과 g 단위")
        NutrientResponse carbohydrate,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "단백질 값과 g 단위")
        NutrientResponse protein,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "지방 값과 g 단위")
        NutrientResponse fat) {

    /**
     * JPA 엔티티를 외부 상세 응답 DTO로 변환합니다.
     *
     * @param food 조회된 식품 엔티티
     * @return 외부 식품 코드를 제외한 상세 응답
     */
    public static FoodDetailResponse from(Food food) {
        return new FoodDetailResponse(
                food.getId(),
                food.getFoodName(),
                new NutritionBasisResponse(food.getBasisAmount(), food.getBasisUnit().toLowerCase()),
                new NutrientResponse(food.getEnergy(), "kcal"),
                new NutrientResponse(food.getCarbohydrate(), "g"),
                new NutrientResponse(food.getProtein(), "g"),
                new NutrientResponse(food.getFat(), "g"));
    }
}

package com.nyam.domain.meal.web;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;

import com.nyam.domain.meal.model.Meal;
import com.nyam.domain.meal.model.MealItem;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.annotation.Nullable;

/**
 * 저장된 meal item snapshot만으로 구성한 식사 응답입니다.
 */
@Schema(description = "식사 날짜와 기록 시점 meal item snapshot")
public record MealResponse(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "1", description = "식사 식별자")
        Long mealId,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "2026-08-29",
                description = "요청으로 저장한 식사 기준 날짜")
        LocalDate mealDate,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "요청 순서로 정렬된 item snapshot")
        List<Item> items) {

    /** 현재 food를 다시 읽지 않고 영속 snapshot을 공개 응답으로 변환합니다. */
    public static MealResponse from(Meal meal) {
        return new MealResponse(meal.getId(), meal.getMealDate(), meal.getItems().stream()
                .map(Item::from)
                .toList());
    }

    /** 기록 시점의 food 이름, 섭취량과 주요 영양 snapshot입니다. */
    @Schema(name = "MealItemSnapshotResponse", description = "기록 시점 food와 섭취량 기준 영양 snapshot")
    public record Item(
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "1") Long foodId,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "현미밥") String name,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "150") BigDecimal amount,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED, allowableValues = {"g", "ml"}) String unit,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) Nutrient energy,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) Nutrient carbohydrate,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) Nutrient protein,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) Nutrient fat) {

        static Item from(MealItem item) {
            return new Item(
                    item.getFoodId(),
                    item.getFoodNameSnapshot(),
                    item.getConsumedAmount(),
                    lower(item.getConsumedUnit()),
                    new Nutrient(item.getEnergySnapshot(), lower(item.getEnergyUnit())),
                    new Nutrient(item.getCarbohydrateSnapshot(), lower(item.getCarbohydrateUnit())),
                    new Nutrient(item.getProteinSnapshot(), lower(item.getProteinUnit())),
                    new Nutrient(item.getFatSnapshot(), lower(item.getFatUnit())));
        }

        private static String lower(String unit) {
            return unit.toLowerCase(Locale.ROOT);
        }
    }

    /** 값이 없을 때도 단위를 유지하는 영양 snapshot 응답입니다. */
    @Schema(name = "MealNutrientSnapshotResponse", description = "nullable 영양값과 기록 시점 단위")
    public record Nutrient(
            @Nullable
            @Schema(nullable = true, description = "원천 값이 없으면 0이 아니라 null인 섭취량 기준 영양값")
            BigDecimal value,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED, allowableValues = {"kcal", "g"})
            String unit) {
    }
}

package com.nyam.domain.meal.web;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import com.nyam.domain.meal.service.CreateMealCommand;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/**
 * 사용자가 지정한 식사 날짜와 food별 섭취량만 받는 생성 요청입니다.
 */
@Schema(description = "식사 날짜와 1~20개의 food 섭취량. 소유자와 snapshot 값은 서버가 결정합니다.")
public record CreateMealRequest(
        @NotNull
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "2026-08-29",
                description = "사용자가 지정한 식사 기준 날짜. 미래 날짜도 허용합니다.")
        LocalDate mealDate,
        @NotNull
        @Size(min = 1, max = 20)
        @Valid
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED,
                description = "서로 다른 food 식별자와 해당 기준 단위의 섭취량 목록")
        List<Item> items) {

    /** 서비스가 사용할 HTTP 독립 생성 입력으로 변환합니다. */
    public CreateMealCommand toCommand() {
        return new CreateMealCommand(mealDate, items.stream()
                .map(item -> new CreateMealCommand.Item(item.foodId(), item.amount()))
                .toList());
    }

    /**
     * 한 food의 양수 섭취량입니다.
     */
    @Schema(name = "CreateMealItemRequest", description = "한 food와 기준 단위 섭취량")
    public record Item(
            @NotNull
            @Positive
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "1", description = "food 식별자")
            Long foodId,
            @NotNull
            @DecimalMin(value = "0", inclusive = false)
            @DecimalMax("10000")
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "150",
                    description = "food 기준 단위의 섭취량. 값 변경 없이 scale 4로 표현 가능해야 합니다.")
            BigDecimal amount) {
    }
}

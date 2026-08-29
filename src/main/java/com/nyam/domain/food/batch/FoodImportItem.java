package com.nyam.domain.food.batch;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 검증과 정규화가 끝나 JDBC upsert에 전달되는 식품 한 건입니다.
 *
 * @param sourceFoodCode 공공 원천 식품 코드
 * @param foodName 사용자 표시용 원본 식품명
 * @param normalizedName 접두사 검색용 정규화 식품명
 * @param foodType 원천 식품 유형
 * @param basisAmount 영양정보 기준량
 * @param basisUnit 기준 단위
 * @param energy 에너지 값 또는 {@code null}
 * @param carbohydrate 탄수화물 값 또는 {@code null}
 * @param protein 단백질 값 또는 {@code null}
 * @param fat 지방 값 또는 {@code null}
 * @param importedAt 최초 삽입 또는 실제 변경 시각
 */
public record FoodImportItem(
        String sourceFoodCode,
        String foodName,
        String normalizedName,
        String foodType,
        BigDecimal basisAmount,
        String basisUnit,
        BigDecimal energy,
        BigDecimal carbohydrate,
        BigDecimal protein,
        BigDecimal fat,
        LocalDateTime importedAt) {
}

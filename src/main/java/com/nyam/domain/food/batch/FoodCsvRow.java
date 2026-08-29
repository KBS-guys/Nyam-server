package com.nyam.domain.food.batch;

import java.util.List;

/**
 * CSV에서 식품 적재에 사용하는 원본 필드만 안전하게 전달합니다.
 *
 * @param sourceFoodCode 공공 원천 식품 코드
 * @param foodName 원본 식품명
 * @param foodType 원천 데이터 구분 코드
 * @param basis 영양성분 기준량 원문
 * @param energy 에너지 원문
 * @param carbohydrate 탄수화물 원문
 * @param protein 단백질 원문
 * @param fat 지방 원문
 */
public record FoodCsvRow(
        String sourceFoodCode,
        String foodName,
        String foodType,
        String basis,
        String energy,
        String carbohydrate,
        String protein,
        String fat) {

    /**
     * 정확한 45개 필드에서 승인된 위치만 추출합니다.
     *
     * @param fields 파싱이 끝난 CSV 필드
     * @return 적재 대상 원본 필드
     * @throws FoodImportException 필드 수가 45개가 아닌 경우
     */
    public static FoodCsvRow from(List<String> fields) {
        if (fields.size() != FoodCsvSchema.HEADERS.size()) {
            throw new FoodImportException("Food CSV data record does not contain exactly 45 fields");
        }
        return new FoodCsvRow(
                fields.get(0),
                fields.get(1),
                fields.get(2),
                fields.get(4),
                fields.get(5),
                fields.get(10),
                fields.get(7),
                fields.get(8));
    }
}

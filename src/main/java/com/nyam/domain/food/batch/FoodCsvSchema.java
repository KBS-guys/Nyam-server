package com.nyam.domain.food.batch;

import java.util.List;

/**
 * 승인된 MFDS CSV의 정확한 45개 헤더와 적재 필드 위치를 정의합니다.
 */
public final class FoodCsvSchema {

    /** 승인된 원천의 정확한 헤더 순서입니다. */
    public static final List<String> HEADERS = List.of(
            "식품코드", "식품명", "데이터구분코드", "데이터구분명", "영양성분함량기준량",
            "에너지(kcal)", "수분(g)", "단백질(g)", "지방(g)", "회분(g)", "탄수화물(g)",
            "당류(g)", "식이섬유(g)", "칼슘(mg)", "철(mg)", "인(mg)", "칼륨(mg)",
            "나트륨(mg)", "비타민 A(μg RAE)", "레티놀(μg)", "베타카로틴(μg)", "티아민(mg)",
            "리보플라빈(mg)", "니아신(mg)", "비타민 C(mg)", "비타민 D(μg)", "콜레스테롤(mg)",
            "포화지방산(g)", "트랜스지방산(g)", "폐기율(%)", "출처코드", "출처명", "식품중량",
            "수입여부", "원산지국코드", "원산지국명", "품목제조보고번호", "업체명", "제조사명",
            "수입업체명", "유통업체명", "데이터생성방법코드", "데이터생성방법명", "데이터생성일자", "데이터기준일자");

    /**
     * 인스턴스 생성을 막는 스키마 유틸리티 생성자입니다.
     */
    private FoodCsvSchema() {
    }

    /**
     * 파싱된 헤더가 개수, 이름과 순서까지 정확히 일치하는지 확인합니다.
     *
     * @param actual 파싱된 CSV 첫 행
     * @throws FoodImportException 승인된 45개 헤더와 다른 경우
     */
    public static void requireExactHeader(List<String> actual) {
        if (!HEADERS.equals(actual)) {
            throw new FoodImportException("Food CSV header does not match the approved 45-column schema");
        }
    }
}

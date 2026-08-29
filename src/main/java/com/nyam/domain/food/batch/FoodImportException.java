package com.nyam.domain.food.batch;

/**
 * 원본 행이나 로컬 경로를 노출하지 않고 식품 적재 계약 위반을 나타냅니다.
 */
public class FoodImportException extends RuntimeException {

    /**
     * 공개하지 않아도 안전한 적재 실패 사유를 구성합니다.
     *
     * @param message 원본 데이터와 경로를 포함하지 않는 실패 사유
     */
    public FoodImportException(String message) {
        super(message);
    }

    /**
     * 안전한 실패 사유와 내부 원인을 구성합니다.
     *
     * @param message 원본 데이터와 경로를 포함하지 않는 실패 사유
     * @param cause 내부 예외 원인
     */
    public FoodImportException(String message, Throwable cause) {
        super(message, cause);
    }
}

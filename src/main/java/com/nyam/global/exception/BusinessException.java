package com.nyam.global.exception;

import lombok.Getter;

/**
 * 서비스와 정책 계층에서 승인된 공개 오류를 전달하는 비즈니스 예외입니다.
 */
@Getter
public class BusinessException extends RuntimeException {

    /**
     * HTTP 응답으로 변환할 공개 오류 정보입니다.
     */
    private final ErrorCode errorCode;

    /**
     * 지정한 공개 오류 코드와 메시지를 가진 비즈니스 예외를 생성합니다.
     *
     * @param errorCode 외부 응답으로 변환할 공개 오류 정보
     */
    public BusinessException(ErrorCode errorCode) {
        super(errorCode.getMessage());
        this.errorCode = errorCode;
    }
}

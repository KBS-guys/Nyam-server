package com.nyam.global.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

/**
 * API에서 공개할 HTTP 상태, 오류 코드, 안전한 메시지를 한곳에서 정의합니다.
 */
@Getter
public enum ErrorCode {

    /** 예상하지 못한 서버 내부 오류입니다. */
    INTERNAL_SERVER_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_SERVER_ERROR", "서버 에러가 발생했습니다."),
    /** 요청 본문이나 필드 형식이 유효하지 않은 오류입니다. */
    INVALID_INPUT(HttpStatus.BAD_REQUEST, "INVALID_INPUT", "잘못된 요청입니다."),
    /** 인증 정보가 필요한 요청의 오류입니다. */
    UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "E003", "인증이 필요합니다."),
    /** 인증된 사용자가 해당 작업을 수행할 권한이 없는 오류입니다. */
    FORBIDDEN(HttpStatus.FORBIDDEN, "E004", "접근이 거부되었습니다."),
    /** 이메일 또는 로컬 비밀번호가 일치하지 않는 로그인 실패입니다. */
    LOGIN_FAILED(HttpStatus.UNAUTHORIZED, "LOGIN_FAILED", "이메일 또는 비밀번호를 확인해 주세요."),
    /** Refresh Token이 없거나 현재 서버 상태와 일치하지 않는 오류입니다. */
    REFRESH_TOKEN_INVALID(HttpStatus.UNAUTHORIZED, "REFRESH_TOKEN_INVALID", "로그인이 만료되었습니다."),
    /** 쿠키 기반 인증 요청에 필요한 고정 CSRF 표지가 없는 오류입니다. */
    CSRF_REQUEST_REJECTED(HttpStatus.FORBIDDEN, "CSRF_REQUEST_REJECTED", "요청을 처리할 수 없습니다."),

    /** 정규화 이메일이 이미 등록된 오류입니다. */
    EMAIL_ALREADY_REGISTERED(HttpStatus.CONFLICT, "EMAIL_ALREADY_REGISTERED", "이미 가입된 이메일입니다."),
    /** 이메일 검증 증명이 없거나 만료되었거나 유효하지 않은 오류입니다. */
    EMAIL_VERIFICATION_INVALID(HttpStatus.UNPROCESSABLE_ENTITY, "EMAIL_VERIFICATION_INVALID", "이메일 인증 정보가 유효하지 않습니다."),
    /** 인증번호 발송 대기시간, 재전송 횟수 또는 잠금 상태로 발송이 제한된 오류입니다. */
    EMAIL_VERIFICATION_SEND_LIMITED(HttpStatus.TOO_MANY_REQUESTS, "EMAIL_VERIFICATION_SEND_LIMITED",
            "인증번호를 다시 요청할 수 없습니다."),
    /** 현재 인증번호의 최대 확인 시도 횟수를 초과한 오류입니다. */
    EMAIL_VERIFICATION_ATTEMPTS_EXCEEDED(HttpStatus.TOO_MANY_REQUESTS,
            "EMAIL_VERIFICATION_ATTEMPTS_EXCEEDED", "인증번호 확인 횟수를 초과했습니다."),
    /** 로컬 메일 시스템으로 인증번호를 전달하지 못한 오류입니다. */
    EMAIL_DELIVERY_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE, "EMAIL_DELIVERY_UNAVAILABLE",
            "인증 메일을 보낼 수 없습니다."),
    /** 사용자가 회원가입 최소 연령을 충족하지 못한 오류입니다. */
    UNDERAGE_NOT_ALLOWED(HttpStatus.UNPROCESSABLE_ENTITY, "UNDERAGE_NOT_ALLOWED", "만 19세 이상만 가입할 수 있습니다."),
    /** 현재 버전의 필수 동의가 정확히 제출되지 않은 오류입니다. */
    REQUIRED_CONSENT_MISSING(HttpStatus.UNPROCESSABLE_ENTITY, "REQUIRED_CONSENT_MISSING", "필수 동의 항목을 확인해 주세요."),
    /** 비밀번호가 길이 또는 문자 인코딩 정책을 위반한 오류입니다. */
    PASSWORD_POLICY_VIOLATION(HttpStatus.UNPROCESSABLE_ENTITY, "PASSWORD_POLICY_VIOLATION", "비밀번호 정책을 충족하지 않습니다."),
    /** 요청한 식품을 찾을 수 없는 오류입니다. */
    FOOD_NOT_FOUND(HttpStatus.NOT_FOUND, "FOOD_NOT_FOUND", "식품을 찾을 수 없습니다."),
    // USER
    /** 요청한 사용자를 찾을 수 없는 오류입니다. */
    USER_NOT_FOUND(HttpStatus.NOT_FOUND, "U001", "사용자를 찾을 수 없습니다.");

    private final HttpStatus status;
    private final String code;
    private final String message;

    /**
     * 오류 응답에 사용할 공개 계약을 구성합니다.
     *
     * @param status 반환할 HTTP 상태
     * @param code 클라이언트가 분기할 공개 오류 코드
     * @param message 내부 정보를 포함하지 않는 사용자용 메시지
     */
    ErrorCode(HttpStatus status, String code, String message) {
        this.status = status;
        this.code = code;
        this.message = message;
    }
}

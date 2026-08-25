package com.nyam.domain.user.policy;

import java.nio.charset.StandardCharsets;

import org.springframework.stereotype.Component;

import com.nyam.global.exception.BusinessException;
import com.nyam.global.exception.ErrorCode;

/**
 * 로컬 계정 비밀번호의 기본 길이와 BCrypt 바이트 경계를 검증합니다.
 */
@Component
public class PasswordPolicy {

    private static final int MIN_CHARACTERS = 8;
    private static final int MAX_UTF8_BYTES = 72;

    /**
     * 비밀번호를 변경하지 않고 최소 문자 수와 최대 UTF-8 바이트 수를 확인합니다.
     *
     * @param password 사용자가 제출한 평문 비밀번호
     * @return 정책 검증을 통과한 원본 비밀번호
     * @throws BusinessException 비밀번호가 8자 미만이거나 UTF-8 기준 72바이트를 초과한 경우
     */
    public String validate(String password) {
        if (password == null
                || password.length() < MIN_CHARACTERS
                || !isWithinBcryptByteLimit(password)) {
            throw violation();
        }
        return password;
    }

    /**
     * 비밀번호가 BCrypt가 처리할 수 있는 UTF-8 72바이트 이내인지 확인합니다.
     *
     * @param password 검사할 평문 비밀번호
     * @return null이 아니고 UTF-8 기준 최대 바이트 이내이면 {@code true}
     */
    public static boolean isWithinBcryptByteLimit(String password) {
        return password != null && password.getBytes(StandardCharsets.UTF_8).length <= MAX_UTF8_BYTES;
    }

    /**
     * 비밀번호 정책 실패에 사용할 공개 비즈니스 예외를 생성합니다.
     *
     * @return {@code PASSWORD_POLICY_VIOLATION} 비즈니스 예외
     */
    private BusinessException violation() {
        return new BusinessException(ErrorCode.PASSWORD_POLICY_VIOLATION);
    }
}

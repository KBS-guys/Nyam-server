package com.nyam.domain.user.policy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

import com.nyam.global.exception.BusinessException;
import com.nyam.global.exception.ErrorCode;

/**
 * 비밀번호의 기본 문자 수와 BCrypt 바이트 경계 정책을 검증합니다.
 */
class PasswordPolicyTest {

    private final PasswordPolicy policy = new PasswordPolicy();

    /**
     * 최소 8자와 최대 72 UTF-8 바이트의 포함 경계를 허용하는지 검증합니다.
     */
    @Test
    void acceptsInclusiveCharacterAndUtf8Boundaries() {
        assertThat(policy.validate("a".repeat(8))).isEqualTo("a".repeat(8));
        assertThat(policy.validate("a".repeat(72))).isEqualTo("a".repeat(72));
    }

    /**
     * 최소 길이 미만과 BCrypt 최대 바이트 초과 값을 거부하는지 검증합니다.
     */
    @Test
    void rejectsBelowMinimumAndAboveBcryptByteBoundary() {
        assertViolation("a".repeat(7));
        assertViolation("a".repeat(73));
    }

    /**
     * 문자 수를 충족하더라도 UTF-8 인코딩 결과가 72바이트를 넘으면 거부하는지 검증합니다.
     */
    @Test
    void rejectsMultibytePasswordAboveBcryptBoundary() {
        assertViolation("가".repeat(25));
    }

    /**
     * 비밀번호의 의도적인 앞뒤 공백을 제거하지 않는지 검증합니다.
     */
    @Test
    void preservesIntentionalSurroundingWhitespace() {
        String password = " 1234567890123 ";
        assertThat(policy.validate(password)).isEqualTo(password);
    }

    /**
     * 비밀번호 후보가 정책 위반 오류로 거부되는지 확인합니다.
     *
     * @param password 거부를 기대하는 비밀번호 후보
     */
    private void assertViolation(String password) {
        assertThatThrownBy(() -> policy.validate(password))
                .isInstanceOfSatisfying(BusinessException.class,
                        exception -> assertThat(exception.getErrorCode())
                                .isEqualTo(ErrorCode.PASSWORD_POLICY_VIOLATION));
    }
}

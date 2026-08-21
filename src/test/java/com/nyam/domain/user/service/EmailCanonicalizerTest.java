package com.nyam.domain.user.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

import com.nyam.global.exception.BusinessException;
import com.nyam.global.exception.ErrorCode;

/**
 * ASCII 이메일 경계와 표시·정규화 값 분리를 검증합니다.
 */
class EmailCanonicalizerTest {

    private final EmailCanonicalizer canonicalizer = new EmailCanonicalizer();

    /**
     * 바깥 공백만 제거하고 태그와 점은 보존하면서 전체를 소문자로 정규화하는지 확인합니다.
     */
    @Test
    void preservesProviderSignificantCharactersAndLowercasesCanonicalEmail() {
        NormalizedEmailAddress result = canonicalizer.normalize("  User.Name+tag@Example.COM  ");

        assertThat(result.displayEmail()).isEqualTo("User.Name+tag@Example.COM");
        assertThat(result.canonicalEmail()).isEqualTo("user.name+tag@example.com");
    }

    /**
     * 유니코드, 내부 공백, 잘못된 점 위치와 길이 초과 입력을 동일한 입력 오류로 거절하는지 확인합니다.
     */
    @Test
    void rejectsUnsupportedAsciiAndBasicFormatBoundaries() {
        assertInvalid("사용자@example.com");
        assertInvalid("user @example.com");
        assertInvalid(".user@example.com");
        assertInvalid("user@example");
        assertInvalid("a".repeat(245) + "@example.com");
    }

    /**
     * 지정한 이메일이 공개 입력 오류로 거절되는지 확인합니다.
     *
     * @param email 거절되어야 할 이메일
     */
    private void assertInvalid(String email) {
        assertThatThrownBy(() -> canonicalizer.normalize(email))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode())
                .isEqualTo(ErrorCode.INVALID_INPUT);
    }
}

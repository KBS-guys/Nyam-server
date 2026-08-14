package com.nyam.domain.user.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

import org.junit.jupiter.api.Test;

import com.nyam.global.exception.BusinessException;
import com.nyam.global.exception.ErrorCode;

/**
 * 검증 증명의 공개 형식과 SHA-256 해시 계약을 검증합니다.
 */
class VerificationProofHasherTest {

    private final VerificationProofHasher hasher = new VerificationProofHasher();

    /**
     * 정확히 43자인 ASCII 증명을 예상한 SHA-256 값으로 변환하는지 검증합니다.
     *
     * @throws Exception 테스트 환경에서 SHA-256 알고리즘을 제공하지 않는 경우
     */
    @Test
    void hashesTheExactFortyThreeAsciiCharactersWithSha256() throws Exception {
        String proof = "A".repeat(43);

        assertThat(hasher.hash(proof)).isEqualTo(
                MessageDigest.getInstance("SHA-256").digest(proof.getBytes(StandardCharsets.US_ASCII)));
    }

    /**
     * 잘못된 증명 형식을 단일 공개 검증 오류로 변환하는지 검증합니다.
     */
    @Test
    void mapsMalformedProofToTheSinglePublicError() {
        assertThatThrownBy(() -> hasher.hash("not-a-proof"))
                .isInstanceOfSatisfying(BusinessException.class,
                        exception -> assertThat(exception.getErrorCode())
                                .isEqualTo(ErrorCode.EMAIL_VERIFICATION_INVALID));
    }
}

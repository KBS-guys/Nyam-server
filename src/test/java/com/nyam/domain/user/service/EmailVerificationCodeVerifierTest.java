package com.nyam.domain.user.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Base64;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.junit.jupiter.api.Test;

/**
 * 인증번호 HMAC 바이트 계약, 비교, 비밀값 시작 검증을 확인합니다.
 */
class EmailVerificationCodeVerifierTest {

    /**
     * 도메인 접두사·0바이트 구분자·이메일·코드 순서가 승인 계약과 정확히 일치하는지 확인합니다.
     *
     * @throws Exception 테스트 환경에서 HMAC 계산을 수행하지 못한 경우
     */
    @Test
    void hashesTheExactVersionedEmailAndCodeByteContract() throws Exception {
        byte[] secret = new byte[32];
        Arrays.fill(secret, (byte) 0x5a);
        EmailVerificationCodeVerifier verifier = new EmailVerificationCodeVerifier(
                Base64.getEncoder().encodeToString(secret));
        String email = "test+tag@example.com";
        String code = String.format("%06d", 271_828);

        byte[] actual = verifier.hash(email, code);
        byte[] expected = expectedHash(secret, email, code);

        assertThat(actual).containsExactly(expected);
        assertThat(verifier.matches(email, code, expected)).isTrue();
        assertThat(verifier.matches(email, differentCode(code), expected)).isFalse();
    }

    /**
     * 디코딩 결과가 32바이트보다 짧거나 Base64가 아니면 시작 단계에서 거절하는지 확인합니다.
     */
    @Test
    void rejectsMissingStrengthAndMalformedSecretConfiguration() {
        assertThatThrownBy(() -> new EmailVerificationCodeVerifier(
                Base64.getEncoder().encodeToString(new byte[31])))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> new EmailVerificationCodeVerifier("not-base64"))
                .isInstanceOf(IllegalStateException.class);
    }

    /**
     * 운영 코드와 독립된 순서로 승인 HMAC 결과를 계산합니다.
     *
     * @param secret 테스트용 HMAC 키 바이트
     * @param email 정규화 이메일
     * @param code 6자리 인증번호
     * @return 승인 바이트 계약의 HMAC 결과
     * @throws Exception 테스트 환경에서 HMAC 계산을 수행하지 못한 경우
     */
    private byte[] expectedHash(byte[] secret, String email, String code) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(secret, "HmacSHA256"));
        mac.update("nyamlog:email-verification-code:v1".getBytes(StandardCharsets.US_ASCII));
        mac.update((byte) 0);
        mac.update(email.getBytes(StandardCharsets.UTF_8));
        mac.update((byte) 0);
        mac.update(code.getBytes(StandardCharsets.US_ASCII));
        return mac.doFinal();
    }

    /**
     * 현재 코드와 반드시 다른 유효한 6자리 문자열을 만듭니다.
     *
     * @param code 기준 인증번호
     * @return 기준값과 다른 6자리 인증번호
     */
    private String differentCode(String code) {
        int next = (Integer.parseInt(code) + 1) % 1_000_000;
        return String.format("%06d", next);
    }
}

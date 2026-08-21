package com.nyam.domain.user.service;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.util.Base64;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * 정규화 이메일과 6자리 인증번호를 승인된 HMAC-SHA-256 계약으로 검증합니다.
 */
@Component
public class EmailVerificationCodeVerifier {

    private static final byte[] DOMAIN_PREFIX =
            "nyamlog:email-verification-code:v1".getBytes(StandardCharsets.US_ASCII);
    private static final int MINIMUM_SECRET_BYTES = 32;
    private final byte[] secret;

    /**
     * 표준 Base64로 전달된 HMAC 비밀값을 검증하고 방어적으로 보관합니다.
     *
     * @param encodedSecret 환경에서 전달된 표준 Base64 비밀값
     * @throws IllegalStateException 값이 없거나 Base64 형식이 아니거나 디코딩 결과가 32바이트 미만인 경우
     */
    public EmailVerificationCodeVerifier(
            @Value("${NYAM_EMAIL_VERIFICATION_HMAC_SECRET}") String encodedSecret) {
        try {
            byte[] decoded = Base64.getDecoder().decode(encodedSecret);
            if (decoded.length < MINIMUM_SECRET_BYTES) {
                throw new IllegalStateException("Email verification HMAC secret is too short");
            }
            this.secret = decoded.clone();
        } catch (IllegalArgumentException | NullPointerException exception) {
            throw new IllegalStateException("Email verification HMAC secret is invalid", exception);
        }
    }

    /**
     * 정규화 이메일과 인증번호를 결합한 32바이트 검증값을 생성합니다.
     *
     * @param canonicalEmail 승인된 ASCII 소문자 정규화 이메일
     * @param verificationCode 앞자리 0을 보존한 6자리 인증번호
     * @return 승인된 바이트 순서로 계산한 HMAC-SHA-256 전체 결과
     * @throws IllegalStateException 실행 환경이 HMAC-SHA-256을 제공하지 않는 경우
     */
    public byte[] hash(String canonicalEmail, String verificationCode) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret, "HmacSHA256"));
            mac.update(DOMAIN_PREFIX);
            mac.update((byte) 0);
            mac.update(canonicalEmail.getBytes(StandardCharsets.UTF_8));
            mac.update((byte) 0);
            mac.update(verificationCode.getBytes(StandardCharsets.US_ASCII));
            return mac.doFinal();
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("HmacSHA256 is not available", exception);
        }
    }

    /**
     * 제출 인증번호의 HMAC을 다시 계산해 저장된 고정 길이 검증값과 비교합니다.
     *
     * @param canonicalEmail 승인된 정규화 이메일
     * @param verificationCode 사용자가 제출한 6자리 인증번호
     * @param expectedVerifier 데이터베이스에 저장된 32바이트 검증값
     * @return 두 검증값이 상수 시간 비교에서 일치하면 {@code true}
     */
    public boolean matches(String canonicalEmail, String verificationCode, byte[] expectedVerifier) {
        return MessageDigest.isEqual(hash(canonicalEmail, verificationCode), expectedVerifier);
    }
}

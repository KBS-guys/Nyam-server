package com.nyam.domain.user.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.regex.Pattern;

import org.springframework.stereotype.Component;

import com.nyam.global.exception.BusinessException;
import com.nyam.global.exception.ErrorCode;

/**
 * 일회성 이메일 검증 증명의 형식을 확인하고 저장 키와 동일한 SHA-256 해시를 생성합니다.
 */
@Component
public class VerificationProofHasher {

    private static final Pattern PROOF_PATTERN = Pattern.compile("[A-Za-z0-9_-]{43}");

    /**
     * URL-safe Base64 형식의 43자 증명을 SHA-256으로 해시합니다.
     *
     * @param rawProof 이메일 확인 기능이 발급한 원문 증명
     * @return 32바이트 SHA-256 해시
     * @throws BusinessException 증명의 형식이 승인된 계약과 다른 경우
     * @throws IllegalStateException 실행 환경에서 SHA-256을 제공하지 않는 경우
     */
    public byte[] hash(String rawProof) {
        if (rawProof == null || !PROOF_PATTERN.matcher(rawProof).matches()) {
            throw new BusinessException(ErrorCode.EMAIL_VERIFICATION_INVALID);
        }
        try {
            return MessageDigest.getInstance("SHA-256")
                    .digest(rawProof.getBytes(StandardCharsets.US_ASCII));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is not available", exception);
        }
    }
}

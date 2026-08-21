package com.nyam.domain.user.service;

import java.security.SecureRandom;
import java.util.Base64;

import org.springframework.stereotype.Component;

/**
 * 회원가입에서 한 번 소비할 고엔트로피 이메일 검증 증명을 생성합니다.
 */
@Component
public class VerificationProofGenerator {

    private static final int PROOF_BYTES = 32;
    private final SecureRandom secureRandom;

    /**
     * 증명 원문 생성에 사용할 암호학적 난수 생성기를 주입받습니다.
     *
     * @param secureRandom 운영체제 난수원을 사용하는 생성기
     */
    public VerificationProofGenerator(SecureRandom secureRandom) {
        this.secureRandom = secureRandom;
    }

    /**
     * 32바이트 난수를 패딩 없는 URL-safe Base64 문자열로 생성합니다.
     *
     * @return 기존 회원가입 계약과 호환되는 43자 일회성 증명
     */
    public String generate() {
        byte[] bytes = new byte[PROOF_BYTES];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}

package com.nyam.domain.user.service;

import java.security.SecureRandom;
import java.util.Locale;

import org.springframework.stereotype.Component;

/**
 * 앞자리 0을 보존하는 균등한 6자리 숫자 인증번호를 생성합니다.
 */
@Component
public class EmailVerificationCodeGenerator {

    private static final int CODE_BOUND = 1_000_000;
    private final SecureRandom secureRandom;

    /**
     * 암호학적 난수 생성기를 주입받습니다.
     *
     * @param secureRandom 운영체제 난수원을 사용하는 생성기
     */
    public EmailVerificationCodeGenerator(SecureRandom secureRandom) {
        this.secureRandom = secureRandom;
    }

    /**
     * 000000부터 999999까지 균등하게 선택한 6자리 문자열을 생성합니다.
     *
     * @return 앞자리 0을 포함할 수 있는 6자리 ASCII 숫자 문자열
     */
    public String generate() {
        return String.format(Locale.ROOT, "%06d", secureRandom.nextInt(CODE_BOUND));
    }
}

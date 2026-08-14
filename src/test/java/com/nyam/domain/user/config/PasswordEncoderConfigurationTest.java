package com.nyam.domain.user.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * 회원가입 비밀번호 인코더의 BCrypt 기본값과 무작위 솔트 적용을 검증합니다.
 */
class PasswordEncoderConfigurationTest {

    /**
     * 생성된 인코더가 서로 다른 BCrypt 해시를 만들고 원문 일치를 확인하는지 검증합니다.
     */
    @Test
    void createsDelegatingBcryptValuesWithRandomizedSalt() {
        PasswordEncoder encoder = new RegistrationConfiguration().passwordEncoder();
        String password = "safe-password-123";

        String first = encoder.encode(password);
        String second = encoder.encode(password);

        assertThat(first).startsWith("{bcrypt}").isNotEqualTo(second);
        assertThat(encoder.matches(password, first)).isTrue();
        assertThat(encoder.matches(password, second)).isTrue();
    }
}

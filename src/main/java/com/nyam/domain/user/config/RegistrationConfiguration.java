package com.nyam.domain.user.config;

import java.time.Clock;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * 회원가입 흐름에서 사용하는 암호화기와 시간 기준을 구성합니다.
 */
@Configuration
public class RegistrationConfiguration {

    /**
     * 저장 형식에 알고리즘 식별자가 포함되는 위임형 비밀번호 인코더를 생성합니다.
     *
     * @return 기본 인코딩 알고리즘으로 BCrypt를 사용하는 비밀번호 인코더
     */
    @Bean
    PasswordEncoder passwordEncoder() {
        return PasswordEncoderFactories.createDelegatingPasswordEncoder();
    }

    /**
     * 회원가입 만료와 생성 시각을 일관되게 계산할 UTC 시계를 생성합니다.
     *
     * @return UTC 시스템 시계
     */
    @Bean
    Clock registrationClock() {
        return Clock.systemUTC();
    }
}

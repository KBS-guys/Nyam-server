package com.nyam.domain.user.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.security.SecureRandom;

import org.junit.jupiter.api.Test;

/**
 * 인증번호 생성 범위와 6자리 문자열 형식을 검증합니다.
 */
class EmailVerificationCodeGeneratorTest {

    /**
     * 난수 상한이 백만 미만이고 작은 숫자의 앞자리 0을 보존하는지 확인합니다.
     */
    @Test
    void usesApprovedBoundAndPreservesLeadingZeroes() {
        SecureRandom random = mock(SecureRandom.class);
        when(random.nextInt(1_000_000)).thenReturn(7);
        EmailVerificationCodeGenerator generator = new EmailVerificationCodeGenerator(random);

        String code = generator.generate();

        assertThat(code).hasSize(6).startsWith("00000").endsWith("7");
        verify(random).nextInt(1_000_000);
    }
}

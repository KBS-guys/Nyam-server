package com.nyam.domain.user.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;

import java.security.SecureRandom;
import java.util.Arrays;

import org.junit.jupiter.api.Test;

/**
 * 일회성 증명이 기존 signup의 43자 URL-safe 형식과 호환되는지 검증합니다.
 */
class VerificationProofGeneratorTest {

    /**
     * 32바이트 난수를 사용하고 패딩 없는 URL-safe Base64 문자열을 생성하는지 확인합니다.
     */
    @Test
    void generatesFortyThreeUrlSafeCharactersFromThirtyTwoRandomBytes() {
        SecureRandom random = mock(SecureRandom.class);
        doAnswer(invocation -> {
            byte[] bytes = invocation.getArgument(0);
            Arrays.fill(bytes, (byte) 0x33);
            return null;
        }).when(random).nextBytes(any(byte[].class));
        VerificationProofGenerator generator = new VerificationProofGenerator(random);

        String proof = generator.generate();

        assertThat(proof).hasSize(43).matches("[A-Za-z0-9_-]{43}");
    }
}

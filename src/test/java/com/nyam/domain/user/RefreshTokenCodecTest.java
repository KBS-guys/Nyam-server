package com.nyam.domain.user;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

import org.junit.jupiter.api.Test;

import com.nyam.domain.user.service.RefreshTokenCodec;

/**
 * 불투명 Refresh Token의 난수 형식과 SHA-256 저장 경계를 검증합니다.
 */
class RefreshTokenCodecTest {

    private final RefreshTokenCodec codec = new RefreshTokenCodec();

    /**
     * 생성 토큰이 32바이트 URL-safe Base64 무패딩 형식이고 반복 발급 시 달라지는지 확인합니다.
     */
    @Test
    void generatesDistinctFortyThreeCharacterUrlSafeTokens() {
        String first = codec.generate();
        String second = codec.generate();

        assertThat(first).matches("[A-Za-z0-9_-]{43}");
        assertThat(second).matches("[A-Za-z0-9_-]{43}").isNotEqualTo(first);
    }

    /**
     * 정확한 공개 형식만 ASCII 전체 값의 SHA-256으로 변환하는지 확인합니다.
     *
     * @throws Exception 테스트 런타임에서 SHA-256을 제공하지 못한 경우
     */
    @Test
    void hashesOnlyTheExactPublicFormat() throws Exception {
        String token = "A".repeat(43);
        byte[] expected = MessageDigest.getInstance("SHA-256")
                .digest(token.getBytes(StandardCharsets.US_ASCII));

        assertThat(codec.hashIfValid(token))
                .hasValueSatisfying(hash -> assertThat(hash).containsExactly(expected));
        assertThat(codec.hashIfValid(null)).isEmpty();
        assertThat(codec.hashIfValid("A".repeat(42))).isEmpty();
        assertThat(codec.hashIfValid("A".repeat(42) + "=")).isEmpty();
    }
}

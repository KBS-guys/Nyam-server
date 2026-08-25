package com.nyam.domain.user.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Optional;
import java.util.regex.Pattern;

import org.springframework.stereotype.Component;

/**
 * 불투명 Refresh Token을 생성하고 형식을 검사한 뒤 SHA-256 해시로 변환합니다.
 */
@Component
public class RefreshTokenCodec {

    private static final int TOKEN_BYTES = 32;
    private static final int TOKEN_LENGTH = 43;
    private static final Pattern TOKEN_PATTERN = Pattern.compile("[A-Za-z0-9_-]{43}");

    private final SecureRandom secureRandom = new SecureRandom();

    /**
     * 256비트 난수를 URL-safe Base64 불투명 토큰으로 생성합니다.
     *
     * @return 패딩이 없는 43자 Refresh Token 원문
     */
    public String generate() {
        byte[] bytes = new byte[TOKEN_BYTES];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    /**
     * 공개 형식을 만족하는 토큰만 SHA-256 해시로 변환합니다.
     *
     * @param rawToken 쿠키로 전달된 Refresh Token 원문
     * @return 유효한 형식이면 32바이트 해시, 아니면 빈 값
     */
    public Optional<byte[]> hashIfValid(String rawToken) {
        if (rawToken == null
                || rawToken.length() != TOKEN_LENGTH
                || !TOKEN_PATTERN.matcher(rawToken).matches()) {
            return Optional.empty();
        }
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return Optional.of(digest.digest(rawToken.getBytes(StandardCharsets.US_ASCII)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }
}

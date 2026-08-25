package com.nyam.domain.user.service;

/**
 * 트랜잭션 커밋 후 로그인 또는 재발급 응답을 구성하는 민감한 발급 결과입니다.
 *
 * @param accessToken 응답 본문에만 전달할 Access Token
 * @param refreshToken HttpOnly 쿠키에만 전달할 Refresh Token
 * @param refreshMaxAgeSeconds Refresh Token 쿠키의 남은 수명
 */
public record IssuedAuthentication(
        String accessToken,
        String refreshToken,
        long refreshMaxAgeSeconds) {

    /**
     * 로그와 디버거 문자열에서 두 토큰 원문을 숨깁니다.
     *
     * @return 토큰 값이 마스킹된 발급 결과 요약
     */
    @Override
    public String toString() {
        return "IssuedAuthentication[accessToken=<redacted>, refreshToken=<redacted>, refreshMaxAgeSeconds="
                + refreshMaxAgeSeconds + "]";
    }
}

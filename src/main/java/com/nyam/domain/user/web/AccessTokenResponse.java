package com.nyam.domain.user.web;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 로그인과 재발급 성공 시 응답 본문으로 전달하는 Access Token 정보입니다.
 *
 * @param accessToken 메모리에만 보관하고 Bearer 헤더로 사용할 Access Token
 * @param tokenType 고정 Bearer 토큰 형식
 * @param expiresInSeconds Access Token 수명(초)
 */
@Schema(description = "로그인 또는 재발급으로 반환되는 단기 Access Token 정보")
public record AccessTokenResponse(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, accessMode = Schema.AccessMode.READ_ONLY,
                description = "보호 API의 Authorization Bearer 헤더에 사용할 단기 JWT입니다. 영구 저장하지 않습니다.")
        String accessToken,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, allowableValues = "Bearer",
                description = "Authorization 헤더에 사용하는 인증 형식입니다.")
        String tokenType,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, minimum = "900", maximum = "900",
                description = "Access Token의 고정 수명(초)입니다.")
        long expiresInSeconds) {

    /**
     * 로그와 디버거 문자열에서 Access Token 원문을 숨깁니다.
     *
     * @return Access Token이 마스킹된 응답 요약
     */
    @Override
    public String toString() {
        return "AccessTokenResponse[accessToken=<redacted>, tokenType=" + tokenType
                + ", expiresInSeconds=" + expiresInSeconds + "]";
    }
}

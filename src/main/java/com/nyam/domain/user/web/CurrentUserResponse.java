package com.nyam.domain.user.web;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 인증 주체에서 조회한 현재 사용자의 공개 정보입니다.
 *
 * @param email 가입 시 보존한 표기 이메일
 */
@Schema(description = "Bearer 인증 주체로 조회한 현재 사용자 정보")
public record CurrentUserResponse(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, format = "email",
                description = "클라이언트 식별자가 아니라 SecurityContext 주체로 조회한 표기 이메일입니다.")
        String email) {
}

package com.nyam.domain.user.web;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 회원가입 완료 후 외부에 공개할 최소 응답 데이터입니다.
 *
 * @param email 소비된 검증 증명에 결합된 원본 표기 이메일
 */
@Schema(description = "회원가입 완료 후 반환되는 결과 데이터")
public record SignupResponse(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED,
                description = "소비된 이메일 인증 증명에 결합되어 있던 원본 표기 이메일입니다. "
                        + "내부 사용자 식별자, 비밀번호, 인증 증명, Access Token은 반환하지 않습니다.")
        String email) {
}

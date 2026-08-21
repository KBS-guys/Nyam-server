package com.nyam.domain.user.web;

import java.time.Instant;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 이메일 확인 성공 후 기존 회원가입에 전달할 일회성 증명을 반환합니다.
 *
 * @param verificationProof 기존 signup 요청에 그대로 전달할 일회성 증명
 * @param proofExpiresAt 일회성 증명 만료 시각
 */
@Schema(description = "이메일 확인 성공 후 회원가입에 사용할 일회성 증명")
public record EmailVerificationConfirmResponse(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, accessMode = Schema.AccessMode.READ_ONLY,
                minLength = 43, maxLength = 43,
                description = "기존 signup 요청의 verificationProof 필드에 한 번만 제출할 43자 증명입니다. "
                        + "민감값이므로 예시와 기본값을 제공하지 않습니다.")
        String verificationProof,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, format = "date-time",
                description = "발급된 증명을 signup에서 사용할 수 없게 되는 UTC 시각입니다.")
        Instant proofExpiresAt) {

    /**
     * 로그나 디버거에서 일회성 증명을 노출하지 않는 문자열을 반환합니다.
     *
     * @return 증명이 마스킹된 응답 요약
     */
    @Override
    public String toString() {
        return "EmailVerificationConfirmResponse[verificationProof=<redacted>, proofExpiresAt="
                + proofExpiresAt + "]";
    }
}

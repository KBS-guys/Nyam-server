package com.nyam.domain.user.web;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

/**
 * 가입 전 인증번호를 받을 이메일을 제출하는 요청입니다.
 *
 * @param email 바깥 공백 제거 후 검증할 ASCII 이메일
 */
@Schema(description = "가입 전 이메일 인증번호 발송 요청")
public record EmailVerificationSendRequest(
        @NotNull
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, maxLength = 254, format = "email",
                description = "앞뒤 공백을 제거한 뒤 최대 254자의 ASCII 이메일만 허용합니다. "
                        + "국제화 이메일과 유니코드 도메인은 지원하지 않으며 +tag와 마침표를 변경하지 않습니다.")
        String email) {
}

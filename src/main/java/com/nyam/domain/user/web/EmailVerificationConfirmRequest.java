package com.nyam.domain.user.web;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

/**
 * 이메일에 도착한 현재 인증번호를 확인하는 요청입니다.
 *
 * @param email 인증번호 발송을 요청했던 이메일
 * @param verificationCode 앞자리 0을 보존한 6자리 인증번호
 */
@Schema(description = "이메일에 도착한 현재 인증번호 확인 요청")
public record EmailVerificationConfirmRequest(
        @NotNull
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, maxLength = 254, format = "email",
                description = "발송 요청과 동일한 ASCII 이메일입니다. 대소문자는 동일 이메일로 정규화합니다.")
        String email,

        @NotNull
        @Pattern(regexp = "[0-9]{6}")
        @Schema(accessMode = Schema.AccessMode.WRITE_ONLY, requiredMode = Schema.RequiredMode.REQUIRED,
                minLength = 6, maxLength = 6, pattern = "[0-9]{6}",
                description = "메일로 받은 정확히 6자리 ASCII 숫자입니다. 앞자리 0을 포함할 수 있으며 예시나 로그에 노출하지 않습니다.")
        String verificationCode) {

    /**
     * 로그나 디버거에서 인증번호를 노출하지 않는 문자열을 반환합니다.
     *
     * @return 인증번호가 마스킹된 요청 요약
     */
    @Override
    public String toString() {
        return "EmailVerificationConfirmRequest[email=" + email + ", verificationCode=<redacted>]";
    }
}

package com.nyam.domain.user.web;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import com.nyam.domain.user.policy.PasswordPolicy;

/**
 * 로컬 계정 로그인을 위해 제출하는 이메일과 비밀번호입니다.
 *
 * @param email 기존 ASCII canonicalization 규칙으로 확인할 이메일
 * @param password 변경하거나 정규화하지 않고 검증할 평문 비밀번호
 */
@Schema(description = "이메일과 비밀번호를 사용하는 로컬 로그인 요청")
public record LoginRequest(
        @NotNull
        @Size(min = 1, max = 254)
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, maxLength = 254, format = "email",
                description = "회원가입에 사용한 이메일입니다. 기존 ASCII canonicalization 규칙을 적용합니다.")
        String email,

        @NotNull
        @Size(min = 1, max = 72)
        @Schema(accessMode = Schema.AccessMode.WRITE_ONLY, requiredMode = Schema.RequiredMode.REQUIRED,
                format = "password", maxLength = 72,
                description = "저장된 BCrypt 자격 증명과 비교할 UTF-8 기준 최대 72바이트 비밀번호입니다. "
                        + "값은 응답·로그·예시에 노출되지 않습니다.")
        String password) {

    /**
     * 로그인 비밀번호가 BCrypt의 UTF-8 바이트 한계를 넘지 않는지 확인합니다.
     *
     * @return 누락 값은 필수 검증에 위임하고, 값이 있으면 BCrypt 바이트 한계 이내일 때 {@code true}
     */
    @AssertTrue
    @Schema(hidden = true)
    public boolean isPasswordWithinBcryptByteLimit() {
        return password == null || PasswordPolicy.isWithinBcryptByteLimit(password);
    }

    /**
     * 로그나 디버거에서 비밀번호를 노출하지 않는 문자열을 반환합니다.
     *
     * @return 비밀번호가 마스킹된 요청 요약
     */
    @Override
    public String toString() {
        return "LoginRequest[email=" + email + ", password=<redacted>]";
    }
}

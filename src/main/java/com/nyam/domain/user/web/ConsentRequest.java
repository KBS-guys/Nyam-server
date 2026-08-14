package com.nyam.domain.user.web;

import com.nyam.domain.user.model.ConsentType;
import com.nyam.domain.user.policy.ConsentAgreement;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

/**
 * 회원가입 HTTP 요청에서 전달받는 개별 동의 항목입니다.
 *
 * @param type 동의 항목 종류를 나타내는 공개 문자열
 * @param version 사용자가 동의한 정책 버전
 */
@Schema(description = "회원가입 시 제출하는 개별 필수 동의 항목")
public record ConsentRequest(
        @NotBlank
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "TERMS",
                allowableValues = {"TERMS", "PERSONAL_INFORMATION", "HEALTH_INFORMATION"},
                description = "동의 종류입니다. TERMS는 서비스 이용약관, PERSONAL_INFORMATION은 개인정보 수집·이용, "
                        + "HEALTH_INFORMATION은 건강정보 처리를 의미합니다. 알 수 없는 값은 필수 동의 오류로 처리됩니다.")
        String type,

        @NotBlank
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "1.0",
                description = "사용자가 동의한 정책 버전입니다. 회원가입 시점에 서버가 허용하는 현재 버전이어야 합니다.")
        String version) {

    /**
     * 웹 요청 표현을 서비스 계층의 동의 값으로 변환합니다.
     *
     * @return 검증 대상으로 전달할 동의 값
     */
    ConsentAgreement toAgreement() {
        return new ConsentAgreement(parseType(), version);
    }

    /**
     * 공개 동의 문자열을 내부 enum으로 변환하고 알 수 없는 값은 정책 계층이 판정하도록 남깁니다.
     *
     * @return 알려진 동의 종류이며 알 수 없는 문자열이면 {@code null}
     */
    private ConsentType parseType() {
        if (type == null) {
            return null;
        }
        try {
            return ConsentType.valueOf(type);
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }
}

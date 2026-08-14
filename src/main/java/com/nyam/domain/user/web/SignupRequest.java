package com.nyam.domain.user.web;

import java.time.LocalDate;
import java.util.List;

import com.nyam.domain.user.service.RegisterUserCommand;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * 이메일 확인을 마친 사용자가 로컬 계정 생성을 위해 제출하는 최종 요청입니다.
 *
 * @param verificationProof 이메일 확인 후 발급된 일회성 증명
 * @param password 사용자가 설정할 평문 비밀번호
 * @param birthDate 가입 연령 확인에 사용할 생년월일
 * @param consents 필수 동의 세 항목
 */
@Schema(description = "이메일 인증을 완료한 사용자가 로컬 계정 생성을 위해 제출하는 최종 회원가입 요청")
public record SignupRequest(
        @NotNull
        @Schema(accessMode = Schema.AccessMode.WRITE_ONLY, requiredMode = Schema.RequiredMode.REQUIRED,
                minLength = 43, maxLength = 43, pattern = "[A-Za-z0-9_-]{43}",
                description = "이메일 인증 완료 후 발급되는 43자 URL-safe Base64 형식의 일회성 증명입니다. "
                        + "서버가 검증된 이메일을 이 증명에서 확인하므로 요청에 이메일을 별도로 제출하지 않습니다. "
                        + "값이 제출되었지만 형식이 잘못된 경우에도 인증 실패와 동일한 422 응답을 반환합니다.")
        String verificationProof,

        @NotNull
        @Schema(accessMode = Schema.AccessMode.WRITE_ONLY, requiredMode = Schema.RequiredMode.REQUIRED,
                format = "password", minLength = 8,
                description = "사용자가 설정할 비밀번호입니다. 8자 이상이어야 하며 BCrypt가 안전하게 처리할 수 있도록 "
                        + "UTF-8 기준 72바이트를 초과할 수 없습니다. 값은 예시나 응답에 노출되지 않습니다.")
        String password,

        @NotNull
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, format = "date", example = "2000-01-01",
                description = "가입일 기준 만 19세 이상인지 확인할 생년월일입니다. 미래 날짜와 만 19세 미만은 허용하지 않습니다.")
        LocalDate birthDate,

        @NotNull
        @Size(min = 3, max = 3)
        @Valid
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED,
                description = "현재 버전의 서비스 이용약관, 개인정보 수집·이용, 건강정보 처리 동의를 각각 한 번씩 제출합니다. "
                        + "정확히 세 항목이어야 하며 중복이나 누락은 허용하지 않습니다.")
        List<ConsentRequest> consents) {

    /**
     * 검증된 HTTP 요청을 회원가입 서비스 명령으로 변환합니다.
     *
     * @return 서비스 계층에 전달할 회원가입 명령
     */
    RegisterUserCommand toCommand() {
        return new RegisterUserCommand(
                verificationProof,
                password,
                birthDate,
                consents.stream().map(ConsentRequest::toAgreement).toList());
    }

    /**
     * 로그나 디버거에서 민감한 증명과 비밀번호를 노출하지 않는 문자열을 반환합니다.
     *
     * @return 민감값이 마스킹된 요청 요약
     */
    @Override
    public String toString() {
        return "SignupRequest[verificationProof=<redacted>, password=<redacted>, birthDate="
                + birthDate + ", consentCount=" + (consents == null ? 0 : consents.size()) + "]";
    }
}

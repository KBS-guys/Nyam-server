package com.nyam.domain.user.web;

import java.time.LocalDate;

import com.nyam.domain.user.service.RegisterUserCommand;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

/**
 * 이메일 확인을 마친 사용자가 로컬 계정 생성을 위해 제출하는 최종 요청입니다.
 *
 * @param email 인증번호를 발급받은 이메일
 * @param verificationCode 메일로 받은 현재 6자리 인증번호
 * @param password 사용자가 설정할 평문 비밀번호
 * @param birthDate 가입 연령 확인에 사용할 생년월일
 * @param termsAgreed 서비스 이용약관 동의 여부
 * @param personalInformationAgreed 개인정보 수집·이용 동의 여부
 * @param healthInformationAgreed 건강정보 처리 동의 여부
 */
@Schema(description = "메일로 받은 현재 인증번호를 직접 검증해 로컬 계정을 생성하는 요청")
public record SignupRequest(
        @NotNull
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, maxLength = 254, format = "email",
                description = "인증번호를 발급받은 ASCII 이메일입니다. challenge 조회에는 소문자 canonical identity를 사용합니다.")
        String email,

        @NotNull
        @Pattern(regexp = "[0-9]{6}")
        @Schema(accessMode = Schema.AccessMode.WRITE_ONLY, requiredMode = Schema.RequiredMode.REQUIRED,
                minLength = 6, maxLength = 6, pattern = "[0-9]{6}",
                description = "메일로 받은 정확히 6자리 ASCII 숫자이며 로그나 응답에 노출하지 않습니다.")
        String verificationCode,

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
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "서비스 이용약관 필수 동의 여부")
        Boolean termsAgreed,

        @NotNull
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "개인정보 수집·이용 필수 동의 여부")
        Boolean personalInformationAgreed,

        @NotNull
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "건강정보 처리 필수 동의 여부")
        Boolean healthInformationAgreed) {

    /**
     * 검증된 HTTP 요청을 회원가입 서비스 명령으로 변환합니다.
     *
     * @return 서비스 계층에 전달할 회원가입 명령
     */
    RegisterUserCommand toCommand() {
        return new RegisterUserCommand(
                email,
                verificationCode,
                password,
                birthDate,
                termsAgreed,
                personalInformationAgreed,
                healthInformationAgreed);
    }

    /**
     * 로그나 디버거에서 민감한 증명과 비밀번호를 노출하지 않는 문자열을 반환합니다.
     *
     * @return 민감값이 마스킹된 요청 요약
     */
    @Override
    public String toString() {
        return "SignupRequest[email=" + email + ", verificationCode=<redacted>, password=<redacted>, birthDate="
                + birthDate + ", termsAgreed=" + termsAgreed
                + ", personalInformationAgreed=" + personalInformationAgreed
                + ", healthInformationAgreed=" + healthInformationAgreed + "]";
    }
}

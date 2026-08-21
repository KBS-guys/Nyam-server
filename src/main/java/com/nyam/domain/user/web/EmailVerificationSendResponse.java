package com.nyam.domain.user.web;

import java.time.Instant;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 인증번호 발송 완료 후 현재 코드의 사용 가능 시각을 반환합니다.
 *
 * @param email 공백을 제거한 메일 수신 표기
 * @param codeExpiresAt 현재 인증번호 만료 시각
 * @param resendAvailableAt 다음 재전송 가능 시각
 */
@Schema(description = "인증번호 발송 완료와 현재 코드의 시간 제한")
public record EmailVerificationSendResponse(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, format = "email",
                description = "서버가 앞뒤 공백만 제거하고 실제 메일 발송에 사용한 이메일 표기입니다.")
        String email,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, format = "date-time",
                description = "현재 인증번호가 만료되는 UTC 시각입니다. 이 시각과 같거나 늦으면 확인할 수 없습니다.")
        Instant codeExpiresAt,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, format = "date-time",
                description = "현재 세션의 횟수 제한을 넘지 않았을 때 다음 재전송이 가능해지는 UTC 시각입니다.")
        Instant resendAvailableAt) {
}

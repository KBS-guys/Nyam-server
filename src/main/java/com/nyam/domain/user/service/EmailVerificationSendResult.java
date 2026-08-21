package com.nyam.domain.user.service;

import java.time.Instant;

/**
 * 인증번호 발송 완료 후 공개 응답으로 전달할 안전한 시각 정보입니다.
 *
 * @param email 공백을 제거한 메일 수신 표기
 * @param codeExpiresAt 현재 인증번호 만료 시각
 * @param resendAvailableAt 다음 재전송 가능 시각
 */
public record EmailVerificationSendResult(
        String email,
        Instant codeExpiresAt,
        Instant resendAvailableAt) {
}

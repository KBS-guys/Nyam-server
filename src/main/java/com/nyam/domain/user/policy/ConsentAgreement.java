package com.nyam.domain.user.policy;

import com.nyam.domain.user.model.ConsentType;

/**
 * 회원가입 서비스 계층이 검증하고 저장할 동의 항목을 표현합니다.
 *
 * @param type 동의 항목 종류
 * @param version 사용자가 동의한 정책 버전
 */
public record ConsentAgreement(ConsentType type, String version) {
}

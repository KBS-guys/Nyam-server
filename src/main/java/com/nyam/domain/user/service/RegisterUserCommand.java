package com.nyam.domain.user.service;

import java.time.LocalDate;

/**
 * 웹 계층에서 회원가입 서비스로 전달하는 검증 전 명령입니다.
 *
 * @param email 인증 challenge를 찾을 이메일
 * @param verificationCode 메일로 받은 현재 6자리 인증번호
 * @param password 사용자가 제출한 평문 비밀번호
 * @param birthDate 성인 자격 확인에 사용할 생년월일
 * @param termsAgreed 서비스 이용약관 동의 여부
 * @param personalInformationAgreed 개인정보 수집·이용 동의 여부
 * @param healthInformationAgreed 건강정보 처리 동의 여부
 */
public record RegisterUserCommand(
        String email,
        String verificationCode,
        String password,
        LocalDate birthDate,
        Boolean termsAgreed,
        Boolean personalInformationAgreed,
        Boolean healthInformationAgreed) {
}

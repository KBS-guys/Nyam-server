package com.nyam.domain.user.service;

import java.time.LocalDate;
import java.util.List;

import com.nyam.domain.user.policy.ConsentAgreement;

/**
 * 웹 계층에서 회원가입 서비스로 전달하는 검증 전 명령입니다.
 *
 * @param verificationProof 이메일 확인 후 발급된 일회성 원문 증명
 * @param password 사용자가 제출한 평문 비밀번호
 * @param birthDate 성인 자격 확인에 사용할 생년월일
 * @param consents 사용자가 제출한 필수 동의 목록
 */
public record RegisterUserCommand(
        String verificationProof,
        String password,
        LocalDate birthDate,
        List<ConsentAgreement> consents) {
}

package com.nyam.domain.user.service;

import java.time.Instant;

import com.nyam.global.exception.ErrorCode;

/**
 * 확인 트랜잭션의 커밋 가능한 성공 또는 실패 결과를 전달합니다.
 *
 * @param verificationProof 성공 시 한 번만 반환할 원문 증명
 * @param proofExpiresAt 성공 시 증명 만료 시각
 * @param errorCode 실패 횟수를 커밋한 뒤 웹 계층에서 반환할 공개 오류
 */
public record EmailVerificationConfirmationResult(
        String verificationProof,
        Instant proofExpiresAt,
        ErrorCode errorCode) {

    /**
     * 발급된 증명과 만료 시각을 가진 성공 결과를 생성합니다.
     *
     * @param verificationProof 새로 발급된 원문 증명
     * @param proofExpiresAt 증명 만료 시각
     * @return 오류 코드가 없는 성공 결과
     */
    public static EmailVerificationConfirmationResult success(
            String verificationProof, Instant proofExpiresAt) {
        return new EmailVerificationConfirmationResult(verificationProof, proofExpiresAt, null);
    }

    /**
     * 트랜잭션이 정상 커밋된 뒤 공개 오류로 변환할 실패 결과를 생성합니다.
     *
     * @param errorCode 확인 실패를 설명하는 공개 오류
     * @return 증명 정보가 없는 실패 결과
     */
    public static EmailVerificationConfirmationResult failure(ErrorCode errorCode) {
        return new EmailVerificationConfirmationResult(null, null, errorCode);
    }

    /**
     * 원문 증명이 로그나 디버거 문자열에 노출되지 않는 요약을 반환합니다.
     *
     * @return 민감한 증명을 마스킹한 결과 문자열
     */
    @Override
    public String toString() {
        return "EmailVerificationConfirmationResult[verificationProof=<redacted>, proofExpiresAt="
                + proofExpiresAt + ", errorCode=" + errorCode + "]";
    }
}

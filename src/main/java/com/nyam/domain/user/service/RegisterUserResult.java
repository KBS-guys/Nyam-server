package com.nyam.domain.user.service;

import com.nyam.global.exception.ErrorCode;

/**
 * 회원가입 트랜잭션이 커밋한 성공 또는 인증번호 실패 결과입니다.
 *
 * <p>인증번호 불일치 횟수를 먼저 커밋한 뒤 웹 계층이 공개 오류로 변환할 수 있게 합니다.</p>
 */
public record RegisterUserResult(String displayEmail, ErrorCode errorCode) {

    /**
     * @param displayEmail challenge가 보존한 표시 이메일
     * @return 회원가입 성공 결과
     */
    public static RegisterUserResult success(String displayEmail) {
        return new RegisterUserResult(displayEmail, null);
    }

    /**
     * @param errorCode 트랜잭션 커밋 후 공개할 인증번호 실패 코드
     * @return 회원가입 실패 결과
     */
    public static RegisterUserResult failure(ErrorCode errorCode) {
        return new RegisterUserResult(null, errorCode);
    }
}

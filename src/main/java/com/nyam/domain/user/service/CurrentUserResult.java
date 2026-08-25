package com.nyam.domain.user.service;

/**
 * 인증 주체로 조회한 현재 사용자의 공개 가능한 최소 정보입니다.
 *
 * @param displayEmail 가입 시 보존한 사용자 표기 이메일
 */
public record CurrentUserResult(String displayEmail) {
}

package com.nyam.domain.user.service;

/**
 * 공백만 제거한 발송 표기와 중복 비교용 소문자 표기를 함께 전달합니다.
 *
 * @param displayEmail 메일 발송과 응답에 사용할 표기 이메일
 * @param canonicalEmail 행 식별과 중복 비교에 사용할 정규화 이메일
 */
public record NormalizedEmailAddress(String displayEmail, String canonicalEmail) {
}

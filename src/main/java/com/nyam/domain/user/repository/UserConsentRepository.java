package com.nyam.domain.user.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.nyam.domain.user.model.UserConsent;

/**
 * 사용자별 약관 및 정보 처리 동의 이력을 저장하는 저장소입니다.
 */
public interface UserConsentRepository extends JpaRepository<UserConsent, Long> {
}

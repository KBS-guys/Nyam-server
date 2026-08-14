package com.nyam.domain.user.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.nyam.domain.user.model.LocalCredential;

/**
 * 사용자별 로컬 비밀번호 자격 증명을 저장하는 저장소입니다.
 */
public interface LocalCredentialRepository extends JpaRepository<LocalCredential, Long> {
}

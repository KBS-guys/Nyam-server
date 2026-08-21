package com.nyam.domain.user.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.nyam.domain.user.model.EmailVerificationChallenge;

import jakarta.persistence.LockModeType;

/**
 * 이메일별 현재 인증 과제의 저장과 쓰기 잠금 조회를 담당합니다.
 */
public interface EmailVerificationChallengeRepository
        extends JpaRepository<EmailVerificationChallenge, String> {

    /**
     * 동일 이메일의 재전송 또는 확인 상태 변경을 직렬화하도록 쓰기 잠금으로 조회합니다.
     *
     * @param canonicalEmail 조회할 정규화 이메일
     * @return 현재 인증 과제이며 존재하지 않으면 빈 값
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select challenge from EmailVerificationChallenge challenge "
            + "where challenge.canonicalEmail = :canonicalEmail")
    Optional<EmailVerificationChallenge> findByCanonicalEmailForUpdate(
            @Param("canonicalEmail") String canonicalEmail);
}

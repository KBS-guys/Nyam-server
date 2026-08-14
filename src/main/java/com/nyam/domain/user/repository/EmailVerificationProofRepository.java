package com.nyam.domain.user.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.nyam.domain.user.model.EmailVerificationProof;

import jakarta.persistence.LockModeType;

/**
 * 이메일 검증 증명의 조회, 잠금, 삭제를 담당하는 저장소입니다.
 */
public interface EmailVerificationProofRepository extends JpaRepository<EmailVerificationProof, byte[]> {

    /**
     * 증명을 소비하는 동안 동일 증명의 동시 사용을 막도록 쓰기 잠금으로 조회합니다.
     *
     * @param proofHash 조회할 원문 증명의 SHA-256 해시
     * @return 일치하는 증명, 존재하지 않으면 빈 값
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select proof from EmailVerificationProof proof where proof.proofHash = :proofHash")
    Optional<EmailVerificationProof> findByProofHashForUpdate(@Param("proofHash") byte[] proofHash);
}

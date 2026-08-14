package com.nyam.domain.user.model;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * 이메일 확인 기능이 발급하고 회원가입이 일회성으로 소비하는 검증 증명입니다.
 *
 * <p>원문 증명은 저장하지 않고 SHA-256 해시와 증명에 결합된 이메일만 저장합니다.</p>
 */
@Entity
@Table(name = "email_verification_proofs")
public class EmailVerificationProof {

    @Id
    @Column(name = "proof_hash", columnDefinition = "BINARY(32)")
    private byte[] proofHash;

    @Column(name = "display_email", nullable = false, length = 254)
    private String displayEmail;

    @Column(name = "canonical_email", nullable = false, length = 254, unique = true)
    private String canonicalEmail;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    /**
     * JPA 엔티티 생성을 위한 기본 생성자입니다.
     */
    protected EmailVerificationProof() {
    }

    /**
     * 발급된 이메일 검증 증명의 저장 상태를 생성합니다.
     *
     * @param proofHash 원문 증명의 SHA-256 해시
     * @param displayEmail 응답과 전달에 사용할 원본 표기 이메일
     * @param canonicalEmail 중복 비교에 사용할 정규화 이메일
     * @param createdAt 증명 생성 시각
     * @param expiresAt 증명 만료 시각
     */
    public EmailVerificationProof(byte[] proofHash, String displayEmail, String canonicalEmail,
            LocalDateTime createdAt, LocalDateTime expiresAt) {
        this.proofHash = proofHash.clone();
        this.displayEmail = displayEmail;
        this.canonicalEmail = canonicalEmail;
        this.createdAt = createdAt;
        this.expiresAt = expiresAt;
    }

    /**
     * 외부 변경으로부터 내부 상태를 보호한 증명 해시 복사본을 반환합니다.
     *
     * @return 증명 해시의 방어적 복사본
     */
    public byte[] getProofHash() {
        return proofHash.clone();
    }

    /**
     * 증명에 결합된 원본 표기 이메일을 반환합니다.
     *
     * @return 원본 표기 이메일
     */
    public String getDisplayEmail() {
        return displayEmail;
    }

    /**
     * 증명에 결합된 정규화 이메일을 반환합니다.
     *
     * @return 중복 비교용 정규화 이메일
     */
    public String getCanonicalEmail() {
        return canonicalEmail;
    }

    /**
     * 증명이 생성된 UTC 시각을 반환합니다.
     *
     * @return 증명 생성 시각
     */
    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    /**
     * 증명을 사용할 수 없게 되는 UTC 시각을 반환합니다.
     *
     * @return 증명 만료 시각
     */
    public LocalDateTime getExpiresAt() {
        return expiresAt;
    }
}

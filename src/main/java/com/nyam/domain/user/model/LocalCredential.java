package com.nyam.domain.user.model;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.MapsId;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;

/**
 * 로컬 계정 사용자의 비밀번호 해시를 사용자와 일대일로 저장합니다.
 */
@Entity
@Table(name = "local_credentials")
public class LocalCredential {

    @Id
    @Column(name = "user_id")
    private Long userId;

    @MapsId
    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private UserAccount user;

    @Column(name = "password_hash", nullable = false, length = 255)
    private String passwordHash;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    /**
     * JPA 엔티티 생성을 위한 기본 생성자입니다.
     */
    protected LocalCredential() {
    }

    /**
     * 신규 사용자의 로컬 인증 정보를 생성합니다.
     *
     * @param user 자격 증명을 소유하는 사용자
     * @param passwordHash 평문이 아닌 인코딩된 비밀번호
     * @param createdAt 자격 증명 생성 시각
     */
    public LocalCredential(UserAccount user, String passwordHash, LocalDateTime createdAt) {
        this.user = user;
        this.passwordHash = passwordHash;
        this.createdAt = createdAt;
    }

    /**
     * 자격 증명을 소유하는 사용자 식별자를 반환합니다.
     *
     * @return 사용자 식별자
     */
    public Long getUserId() {
        return userId;
    }

    /**
     * 저장된 비밀번호 해시를 반환합니다.
     *
     * @return 알고리즘 식별자를 포함한 비밀번호 해시
     */
    public String getPasswordHash() {
        return passwordHash;
    }

    /**
     * 자격 증명이 생성된 UTC 시각을 반환합니다.
     *
     * @return 자격 증명 생성 시각
     */
    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
}

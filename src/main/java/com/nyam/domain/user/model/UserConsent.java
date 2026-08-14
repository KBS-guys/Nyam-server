package com.nyam.domain.user.model;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/**
 * 사용자가 회원가입 시 동의한 항목과 버전, 동의 시각을 기록합니다.
 */
@Entity
@Table(name = "user_consents")
public class UserConsent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "consent_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private UserAccount user;

    @Enumerated(EnumType.STRING)
    @Column(name = "consent_type", nullable = false, length = 32)
    private ConsentType type;

    @Column(name = "consent_version", nullable = false, length = 50)
    private String version;

    @Column(name = "agreed_at", nullable = false, updatable = false)
    private LocalDateTime agreedAt;

    /**
     * JPA 엔티티 생성을 위한 기본 생성자입니다.
     */
    protected UserConsent() {
    }

    /**
     * 특정 사용자에게 귀속되는 동의 기록을 생성합니다.
     *
     * @param user 동의를 제출한 사용자
     * @param type 동의 항목 종류
     * @param version 동의한 약관 또는 정책의 버전
     * @param agreedAt 동의 시각
     */
    public UserConsent(UserAccount user, ConsentType type, String version, LocalDateTime agreedAt) {
        this.user = user;
        this.type = type;
        this.version = version;
        this.agreedAt = agreedAt;
    }

    /**
     * 데이터베이스가 생성한 동의 기록 식별자를 반환합니다.
     *
     * @return 동의 기록 식별자
     */
    public Long getId() {
        return id;
    }

    /**
     * 동의 항목의 종류를 반환합니다.
     *
     * @return 동의 항목 종류
     */
    public ConsentType getType() {
        return type;
    }

    /**
     * 사용자가 동의한 정책 버전을 반환합니다.
     *
     * @return 동의 버전
     */
    public String getVersion() {
        return version;
    }

    /**
     * 사용자가 동의한 UTC 시각을 반환합니다.
     *
     * @return 동의 시각
     */
    public LocalDateTime getAgreedAt() {
        return agreedAt;
    }
}

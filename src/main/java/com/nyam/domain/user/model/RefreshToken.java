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
import jakarta.persistence.UniqueConstraint;

/**
 * 사용자별로 현재 유효한 Refresh Token 해시와 고정 만료 시각을 저장합니다.
 */
@Entity
@Table(name = "refresh_tokens", uniqueConstraints =
        @UniqueConstraint(name = "uk_refresh_tokens_token_hash", columnNames = "token_hash"))
public class RefreshToken {

    @Id
    @Column(name = "user_id")
    private Long userId;

    @MapsId
    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private UserAccount user;

    @Column(name = "token_hash", nullable = false, columnDefinition = "BINARY(32)")
    private byte[] tokenHash;

    @Column(name = "issued_at", nullable = false)
    private LocalDateTime issuedAt;

    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    /**
     * JPA 엔티티 생성을 위한 기본 생성자입니다.
     */
    protected RefreshToken() {
    }

    /**
     * 한 사용자의 현재 Refresh Token 서버 상태를 생성합니다.
     *
     * @param user 토큰 상태를 소유하는 사용자
     * @param tokenHash 원문이 아닌 32바이트 SHA-256 해시
     * @param issuedAt 현재 토큰 발급 시각
     * @param expiresAt 최초 로그인에서 정한 고정 만료 시각
     */
    public RefreshToken(UserAccount user, byte[] tokenHash, LocalDateTime issuedAt, LocalDateTime expiresAt) {
        this.userId = user.getId();
        this.user = user;
        this.tokenHash = tokenHash.clone();
        this.issuedAt = issuedAt;
        this.expiresAt = expiresAt;
    }

    /**
     * 토큰 상태를 소유하는 사용자 식별자를 반환합니다.
     *
     * @return 내부 사용자 식별자
     */
    public Long getUserId() {
        return userId;
    }

    /**
     * 현재 Refresh Token의 해시 복사본을 반환합니다.
     *
     * @return 32바이트 SHA-256 해시 복사본
     */
    public byte[] getTokenHash() {
        return tokenHash.clone();
    }

    /**
     * 현재 토큰이 마지막으로 발급 또는 회전된 시각을 반환합니다.
     *
     * @return UTC 발급 시각
     */
    public LocalDateTime getIssuedAt() {
        return issuedAt;
    }

    /**
     * 회전으로 연장되지 않는 세션 만료 시각을 반환합니다.
     *
     * @return UTC 고정 만료 시각
     */
    public LocalDateTime getExpiresAt() {
        return expiresAt;
    }
}

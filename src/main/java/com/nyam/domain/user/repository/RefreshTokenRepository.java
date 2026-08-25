package com.nyam.domain.user.repository;

import java.time.LocalDateTime;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.nyam.domain.user.model.RefreshToken;

/**
 * 현재 Refresh Token 조회, 조건부 회전, 로그아웃 폐기를 담당합니다.
 */
public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {

    /**
     * 비밀번호 로그인에서 사용자의 현재 토큰 행을 생성하거나 새 30일 세션으로 교체합니다.
     *
     * @param userId 로그인한 사용자 식별자
     * @param tokenHash 새 Refresh Token의 SHA-256 해시
     * @param issuedAt 로그인 시각
     * @param expiresAt 로그인에서 새로 정한 고정 만료 시각
     * @return MySQL이 보고한 삽입 또는 갱신 영향 행 수
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = """
            INSERT INTO refresh_tokens(user_id, token_hash, issued_at, expires_at)
            VALUES (:userId, :tokenHash, :issuedAt, :expiresAt)
            ON DUPLICATE KEY UPDATE
                token_hash = :tokenHash,
                issued_at = :issuedAt,
                expires_at = :expiresAt
            """, nativeQuery = true)
    int saveCurrent(
            @Param("userId") long userId,
            @Param("tokenHash") byte[] tokenHash,
            @Param("issuedAt") LocalDateTime issuedAt,
            @Param("expiresAt") LocalDateTime expiresAt);

    /**
     * 원문에서 계산한 해시와 일치하는 현재 토큰 후보를 잠금 없이 조회합니다.
     *
     * @param tokenHash 제출된 Refresh Token의 SHA-256 해시
     * @return 일치하는 현재 토큰 후보 또는 빈 값
     */
    Optional<RefreshToken> findByTokenHash(byte[] tokenHash);

    /**
     * 사용자·이전 해시·최소 잔여 수명 조건이 모두 유지될 때만 토큰을 원자적으로 회전합니다.
     *
     * @param userId 후보 조회에서 얻은 사용자 식별자
     * @param oldHash 요청으로 제출된 이전 토큰 해시
     * @param newHash 새로 생성한 토큰 해시
     * @param issuedAt 새 토큰 발급 시각
     * @param minimumExpiresAt 쿠키로 표현 가능한 최소 잔여 수명 경계
     * @return 회전에 성공하면 1, 더 이상 현재 토큰이 아니면 0
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = """
            UPDATE refresh_tokens
            SET token_hash = :newHash,
                issued_at = :issuedAt
            WHERE user_id = :userId
              AND token_hash = :oldHash
              AND expires_at >= :minimumExpiresAt
            """, nativeQuery = true)
    int rotate(
            @Param("userId") long userId,
            @Param("oldHash") byte[] oldHash,
            @Param("newHash") byte[] newHash,
            @Param("issuedAt") LocalDateTime issuedAt,
            @Param("minimumExpiresAt") LocalDateTime minimumExpiresAt);

    /**
     * 만료 여부와 관계없이 제출된 해시와 일치하는 현재 토큰 행을 삭제합니다.
     *
     * @param tokenHash 로그아웃 쿠키에서 계산한 SHA-256 해시
     * @return 삭제한 행 수이며 없으면 0
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = "DELETE FROM refresh_tokens WHERE token_hash = :tokenHash", nativeQuery = true)
    int deleteByTokenHashValue(@Param("tokenHash") byte[] tokenHash);
}

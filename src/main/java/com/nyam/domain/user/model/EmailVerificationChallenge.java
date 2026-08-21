package com.nyam.domain.user.model;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * 이메일별 현재 인증번호와 재전송·오입력 상태를 저장하는 인증 과제입니다.
 *
 * <p>원문 인증번호는 보관하지 않으며, 정규화 이메일당 하나의 현재 상태만 유지합니다.</p>
 */
@Entity
@Table(name = "email_verification_challenges")
public class EmailVerificationChallenge {

    @Id
    @Column(name = "canonical_email", length = 254)
    private String canonicalEmail;

    @Column(name = "display_email", nullable = false, length = 254)
    private String displayEmail;

    @Column(name = "code_verifier", nullable = false, columnDefinition = "BINARY(32)")
    private byte[] codeVerifier;

    @Column(name = "verification_started_at", nullable = false)
    private LocalDateTime verificationStartedAt;

    @Column(name = "code_issued_at", nullable = false)
    private LocalDateTime codeIssuedAt;

    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    @Column(name = "resend_count", nullable = false)
    private byte resendCount;

    @Column(name = "failed_attempt_count", nullable = false)
    private byte failedAttemptCount;

    /**
     * JPA 엔티티 생성을 위한 기본 생성자입니다.
     */
    protected EmailVerificationChallenge() {
    }

    /**
     * 처음 발송하는 인증번호의 현재 상태를 생성합니다.
     *
     * @param canonicalEmail 행 식별과 HMAC 검증에 사용할 정규화 이메일
     * @param displayEmail 메일 수신과 응답 표시에 사용할 이메일
     * @param codeVerifier 원문 인증번호의 32바이트 HMAC 검증값
     * @param issuedAt 현재 세션과 인증번호의 발급 시각
     * @param expiresAt 현재 인증번호가 만료되는 시각
     */
    public EmailVerificationChallenge(String canonicalEmail, String displayEmail, byte[] codeVerifier,
            LocalDateTime issuedAt, LocalDateTime expiresAt) {
        this.canonicalEmail = canonicalEmail;
        this.displayEmail = displayEmail;
        this.codeVerifier = codeVerifier.clone();
        this.verificationStartedAt = issuedAt;
        this.codeIssuedAt = issuedAt;
        this.expiresAt = expiresAt;
        this.resendCount = 0;
        this.failedAttemptCount = 0;
    }

    /**
     * 만료된 상태를 새 인증 세션의 첫 인증번호로 교체합니다.
     *
     * @param displayEmail 새 메일 수신과 응답 표시에 사용할 이메일
     * @param codeVerifier 새 인증번호의 32바이트 HMAC 검증값
     * @param issuedAt 새 세션과 인증번호의 발급 시각
     * @param expiresAt 새 인증번호가 만료되는 시각
     */
    public void restart(String displayEmail, byte[] codeVerifier, LocalDateTime issuedAt, LocalDateTime expiresAt) {
        this.displayEmail = displayEmail;
        this.codeVerifier = codeVerifier.clone();
        this.verificationStartedAt = issuedAt;
        this.codeIssuedAt = issuedAt;
        this.expiresAt = expiresAt;
        this.resendCount = 0;
        this.failedAttemptCount = 0;
    }

    /**
     * 현재 세션을 유지하면서 재전송할 새 인증번호로 상태를 교체합니다.
     *
     * @param displayEmail 새 메일 수신과 응답 표시에 사용할 이메일
     * @param codeVerifier 새 인증번호의 32바이트 HMAC 검증값
     * @param issuedAt 새 인증번호의 발급 시각
     * @param expiresAt 새 인증번호가 만료되는 시각
     */
    public void resend(String displayEmail, byte[] codeVerifier, LocalDateTime issuedAt, LocalDateTime expiresAt) {
        this.displayEmail = displayEmail;
        this.codeVerifier = codeVerifier.clone();
        this.codeIssuedAt = issuedAt;
        this.expiresAt = expiresAt;
        this.resendCount++;
        this.failedAttemptCount = 0;
    }

    /**
     * 현재 인증번호와 일치하지 않은 제출 횟수를 한 번 증가시킵니다.
     */
    public void recordMismatch() {
        this.failedAttemptCount++;
    }

    /**
     * 행 식별에 사용하는 정규화 이메일을 반환합니다.
     *
     * @return 정규화 이메일
     */
    public String getCanonicalEmail() {
        return canonicalEmail;
    }

    /**
     * 메일 수신과 응답 표시에 사용하는 이메일을 반환합니다.
     *
     * @return 공백을 제거한 제출 표기 이메일
     */
    public String getDisplayEmail() {
        return displayEmail;
    }

    /**
     * 외부 변경으로부터 보호한 인증번호 검증값을 반환합니다.
     *
     * @return 32바이트 HMAC 검증값의 복사본
     */
    public byte[] getCodeVerifier() {
        return codeVerifier.clone();
    }

    /**
     * 현재 인증 세션이 시작된 시각을 반환합니다.
     *
     * @return 세션 시작 시각
     */
    public LocalDateTime getVerificationStartedAt() {
        return verificationStartedAt;
    }

    /**
     * 현재 인증번호가 발급된 시각을 반환합니다.
     *
     * @return 인증번호 발급 시각
     */
    public LocalDateTime getCodeIssuedAt() {
        return codeIssuedAt;
    }

    /**
     * 현재 인증번호가 만료되는 시각을 반환합니다.
     *
     * @return 인증번호 만료 시각
     */
    public LocalDateTime getExpiresAt() {
        return expiresAt;
    }

    /**
     * 현재 세션에서 성공한 재전송 횟수를 반환합니다.
     *
     * @return 재전송 횟수
     */
    public int getResendCount() {
        return resendCount;
    }

    /**
     * 현재 인증번호에 대한 불일치 횟수를 반환합니다.
     *
     * @return 인증번호 불일치 횟수
     */
    public int getFailedAttemptCount() {
        return failedAttemptCount;
    }
}

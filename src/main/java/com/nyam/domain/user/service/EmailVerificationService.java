package com.nyam.domain.user.service;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Optional;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.nyam.domain.user.model.EmailVerificationChallenge;
import com.nyam.domain.user.repository.EmailVerificationChallengeRepository;
import com.nyam.domain.user.repository.UserAccountRepository;
import com.nyam.global.exception.BusinessException;
import com.nyam.global.exception.ErrorCode;

/**
 * 인증번호 발송 상태와 확인 결과를 MySQL 트랜잭션으로 관리합니다.
 */
@Service
public class EmailVerificationService {

    private static final Duration CODE_LIFETIME = Duration.ofMinutes(5);
    private static final Duration RESEND_DELAY = Duration.ofSeconds(60);
    private static final int MAX_RESENDS = 3;
    private static final int MAX_FAILED_ATTEMPTS = 5;

    private final EmailCanonicalizer emailCanonicalizer;
    private final EmailVerificationCodeGenerator codeGenerator;
    private final EmailVerificationCodeVerifier codeVerifier;
    private final VerificationMailSender mailSender;
    private final EmailVerificationChallengeRepository challengeRepository;
    private final UserAccountRepository userRepository;
    private final Clock clock;

    /**
     * 이메일 인증 수직 흐름에 필요한 정책·저장소·외부 발송 협력 객체를 주입받습니다.
     *
     * @param emailCanonicalizer 이메일 입력 경계와 정규화 정책
     * @param codeGenerator 6자리 인증번호 생성기
     * @param codeVerifier 인증번호 HMAC 생성 및 비교기
     * @param mailSender 로컬 Mailpit 동기 발송기
     * @param challengeRepository 현재 인증 과제 저장소
     * @param userRepository 가입 이메일 중복 확인 저장소
     * @param clock 발급·만료 판단에 사용할 UTC 시계
     */
    public EmailVerificationService(
            EmailCanonicalizer emailCanonicalizer,
            EmailVerificationCodeGenerator codeGenerator,
            EmailVerificationCodeVerifier codeVerifier,
            VerificationMailSender mailSender,
            EmailVerificationChallengeRepository challengeRepository,
            UserAccountRepository userRepository,
            Clock clock) {
        this.emailCanonicalizer = emailCanonicalizer;
        this.codeGenerator = codeGenerator;
        this.codeVerifier = codeVerifier;
        this.mailSender = mailSender;
        this.challengeRepository = challengeRepository;
        this.userRepository = userRepository;
        this.clock = clock;
    }

    /**
     * 가입되지 않은 이메일의 새 인증번호 상태를 저장하고 Mailpit 발송까지 한 트랜잭션으로 처리합니다.
     *
     * <p>메일 발송이 실패하면 신규 행이나 재전송 변경도 함께 롤백됩니다.</p>
     *
     * @param submittedEmail 사용자가 제출한 이메일
     * @return 인증번호 만료와 다음 재전송 가능 시각
     * @throws BusinessException 입력, 가입 중복, 발송 제한 또는 메일 전달 규칙을 충족하지 못한 경우
     */
    @Transactional
    public EmailVerificationSendResult sendCode(String submittedEmail) {
        NormalizedEmailAddress email = emailCanonicalizer.normalize(submittedEmail);
        try {
            return sendCodeAfterChallengeLock(email);
        } catch (CannotAcquireLockException exception) {
            throw new BusinessException(ErrorCode.EMAIL_VERIFICATION_SEND_LIMITED);
        }
    }

    private EmailVerificationSendResult sendCodeAfterChallengeLock(NormalizedEmailAddress email) {
        Optional<EmailVerificationChallenge> current =
                challengeRepository.findByCanonicalEmailForUpdate(email.canonicalEmail());
        if (userRepository.existsByCanonicalEmail(email.canonicalEmail())) {
            throw new BusinessException(ErrorCode.EMAIL_ALREADY_REGISTERED);
        }
        LocalDateTime now = currentUtcTime();
        String code = codeGenerator.generate();
        byte[] verifier = codeVerifier.hash(email.canonicalEmail(), code);
        LocalDateTime expiresAt = now.plus(CODE_LIFETIME);

        if (current.isEmpty()) {
            saveInitialChallenge(email, verifier, now, expiresAt);
        } else {
            updateCurrentChallenge(current.orElseThrow(), email.displayEmail(), verifier, now, expiresAt);
            challengeRepository.flush();
        }

        mailSender.send(email.displayEmail(), code);
        return new EmailVerificationSendResult(
                email.displayEmail(),
                expiresAt.toInstant(ZoneOffset.UTC),
                now.plus(RESEND_DELAY).toInstant(ZoneOffset.UTC));
    }

    /**
     * 동시 최초 삽입 충돌을 발송 제한으로 변환하면서 신규 challenge를 즉시 데이터베이스에 반영합니다.
     *
     * @param email 검증된 이메일 표기 쌍
     * @param verifier 새 인증번호 HMAC 검증값
     * @param issuedAt 발급과 세션 시작 시각
     * @param expiresAt 인증번호 만료 시각
     * @throws BusinessException 동일 이메일의 다른 최초 요청이 먼저 행을 생성한 경우
     */
    private void saveInitialChallenge(NormalizedEmailAddress email, byte[] verifier,
            LocalDateTime issuedAt, LocalDateTime expiresAt) {
        try {
            challengeRepository.saveAndFlush(new EmailVerificationChallenge(
                    email.canonicalEmail(), email.displayEmail(), verifier, issuedAt, expiresAt));
        } catch (DataIntegrityViolationException exception) {
            throw new BusinessException(ErrorCode.EMAIL_VERIFICATION_SEND_LIMITED);
        }
    }

    /**
     * 만료 상태는 새 세션으로 초기화하고 유효 상태는 승인된 재전송 제한을 적용해 교체합니다.
     *
     * @param challenge 쓰기 잠금으로 조회한 현재 상태
     * @param displayEmail 새 메일 수신 표기
     * @param verifier 새 인증번호 HMAC 검증값
     * @param issuedAt 새 인증번호 발급 시각
     * @param expiresAt 새 인증번호 만료 시각
     * @throws BusinessException 대기시간, 최대 재전송 또는 오입력 잠금으로 재전송할 수 없는 경우
     */
    private void updateCurrentChallenge(EmailVerificationChallenge challenge, String displayEmail,
            byte[] verifier, LocalDateTime issuedAt, LocalDateTime expiresAt) {
        if (!issuedAt.isBefore(challenge.getExpiresAt())) {
            challenge.restart(displayEmail, verifier, issuedAt, expiresAt);
            return;
        }
        if (challenge.getFailedAttemptCount() >= MAX_FAILED_ATTEMPTS
                || challenge.getResendCount() >= MAX_RESENDS
                || issuedAt.isBefore(challenge.getCodeIssuedAt().plus(RESEND_DELAY))) {
            throw new BusinessException(ErrorCode.EMAIL_VERIFICATION_SEND_LIMITED);
        }
        challenge.resend(displayEmail, verifier, issuedAt, expiresAt);
    }

    /**
     * 영속 시각과 만료 계산에 사용할 현재 UTC 시각을 한 번 읽습니다.
     *
     * @return 마이크로초 저장이 가능한 UTC 지역 시각
     */
    private LocalDateTime currentUtcTime() {
        return LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC);
    }
}

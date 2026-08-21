package com.nyam.domain.user.service;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.regex.Pattern;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.nyam.domain.user.model.EmailVerificationChallenge;
import com.nyam.domain.user.model.EmailVerificationProof;
import com.nyam.domain.user.repository.EmailVerificationChallengeRepository;
import com.nyam.domain.user.repository.EmailVerificationProofRepository;
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
    private static final Duration PROOF_LIFETIME = Duration.ofMinutes(15);
    private static final int MAX_RESENDS = 3;
    private static final int MAX_FAILED_ATTEMPTS = 5;
    private static final Pattern CODE_PATTERN = Pattern.compile("[0-9]{6}");

    private final EmailCanonicalizer emailCanonicalizer;
    private final EmailVerificationCodeGenerator codeGenerator;
    private final EmailVerificationCodeVerifier codeVerifier;
    private final VerificationProofGenerator proofGenerator;
    private final VerificationProofHasher proofHasher;
    private final VerificationMailSender mailSender;
    private final EmailVerificationChallengeRepository challengeRepository;
    private final EmailVerificationProofRepository proofRepository;
    private final UserAccountRepository userRepository;
    private final Clock clock;

    /**
     * 이메일 인증 수직 흐름에 필요한 정책·저장소·외부 발송 협력 객체를 주입받습니다.
     *
     * @param emailCanonicalizer 이메일 입력 경계와 정규화 정책
     * @param codeGenerator 6자리 인증번호 생성기
     * @param codeVerifier 인증번호 HMAC 생성 및 비교기
     * @param proofGenerator 일회성 증명 원문 생성기
     * @param proofHasher 기존 회원가입 계약과 같은 증명 해시 생성기
     * @param mailSender 로컬 Mailpit 동기 발송기
     * @param challengeRepository 현재 인증 과제 저장소
     * @param proofRepository 회원가입 전달용 증명 저장소
     * @param userRepository 가입 이메일 중복 확인 저장소
     * @param clock 발급·만료 판단에 사용할 UTC 시계
     */
    public EmailVerificationService(
            EmailCanonicalizer emailCanonicalizer,
            EmailVerificationCodeGenerator codeGenerator,
            EmailVerificationCodeVerifier codeVerifier,
            VerificationProofGenerator proofGenerator,
            VerificationProofHasher proofHasher,
            VerificationMailSender mailSender,
            EmailVerificationChallengeRepository challengeRepository,
            EmailVerificationProofRepository proofRepository,
            UserAccountRepository userRepository,
            Clock clock) {
        this.emailCanonicalizer = emailCanonicalizer;
        this.codeGenerator = codeGenerator;
        this.codeVerifier = codeVerifier;
        this.proofGenerator = proofGenerator;
        this.proofHasher = proofHasher;
        this.mailSender = mailSender;
        this.challengeRepository = challengeRepository;
        this.proofRepository = proofRepository;
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
        if (userRepository.existsByCanonicalEmail(email.canonicalEmail())) {
            throw new BusinessException(ErrorCode.EMAIL_ALREADY_REGISTERED);
        }

        Optional<EmailVerificationChallenge> current =
                challengeRepository.findByCanonicalEmailForUpdate(email.canonicalEmail());
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
     * 현재 인증번호를 확인하고 성공 시 challenge를 15분짜리 일회성 proof로 원자적으로 교체합니다.
     *
     * <p>불일치 결과는 예외 대신 반환하여 실패 횟수가 커밋된 뒤 웹 계층에서 오류로 변환되게 합니다.</p>
     *
     * @param submittedEmail 사용자가 제출한 이메일
     * @param verificationCode 앞자리 0을 보존한 6자리 인증번호
     * @return 발급된 proof 또는 커밋 후 반환할 확인 실패 오류
     * @throws BusinessException 이메일이나 인증번호 표현이 입력 계약을 위반한 경우
     */
    @Transactional
    public EmailVerificationConfirmationResult confirmCode(String submittedEmail, String verificationCode) {
        NormalizedEmailAddress email = emailCanonicalizer.normalize(submittedEmail);
        requireCodeFormat(verificationCode);

        Optional<EmailVerificationChallenge> current =
                challengeRepository.findByCanonicalEmailForUpdate(email.canonicalEmail());
        LocalDateTime now = currentUtcTime();
        if (current.isEmpty()) {
            return EmailVerificationConfirmationResult.failure(ErrorCode.EMAIL_VERIFICATION_INVALID);
        }

        EmailVerificationChallenge challenge = current.orElseThrow();
        if (!now.isBefore(challenge.getExpiresAt())) {
            return EmailVerificationConfirmationResult.failure(ErrorCode.EMAIL_VERIFICATION_INVALID);
        }
        if (challenge.getFailedAttemptCount() >= MAX_FAILED_ATTEMPTS) {
            return EmailVerificationConfirmationResult.failure(
                    ErrorCode.EMAIL_VERIFICATION_ATTEMPTS_EXCEEDED);
        }
        if (!codeVerifier.matches(challenge.getCanonicalEmail(), verificationCode,
                challenge.getCodeVerifier())) {
            challenge.recordMismatch();
            challengeRepository.flush();
            ErrorCode error = challenge.getFailedAttemptCount() >= MAX_FAILED_ATTEMPTS
                    ? ErrorCode.EMAIL_VERIFICATION_ATTEMPTS_EXCEEDED
                    : ErrorCode.EMAIL_VERIFICATION_INVALID;
            return EmailVerificationConfirmationResult.failure(error);
        }

        return issueProof(challenge, now);
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
     * 성공한 인증 과제를 삭제하고 기존 미소비 proof를 새 proof로 교체합니다.
     *
     * @param challenge 확인에 성공한 잠금 상태
     * @param now proof 발급 기준 시각
     * @return 새 원문 proof와 만료 시각을 가진 성공 결과
     */
    private EmailVerificationConfirmationResult issueProof(
            EmailVerificationChallenge challenge, LocalDateTime now) {
        String rawProof = proofGenerator.generate();
        byte[] proofHash = proofHasher.hash(rawProof);
        LocalDateTime proofExpiresAt = now.plus(PROOF_LIFETIME);

        challengeRepository.delete(challenge);
        challengeRepository.flush();
        proofRepository.findByCanonicalEmailForUpdate(challenge.getCanonicalEmail())
                .ifPresent(proofRepository::delete);
        proofRepository.flush();
        proofRepository.saveAndFlush(new EmailVerificationProof(
                proofHash,
                challenge.getDisplayEmail(),
                challenge.getCanonicalEmail(),
                now,
                proofExpiresAt));

        return EmailVerificationConfirmationResult.success(
                rawProof, proofExpiresAt.toInstant(ZoneOffset.UTC));
    }

    /**
     * 서비스 직접 호출에서도 인증번호 표현 계약을 강제합니다.
     *
     * @param verificationCode 검사할 인증번호
     * @throws BusinessException 정확히 6자리 ASCII 숫자가 아닌 경우
     */
    private void requireCodeFormat(String verificationCode) {
        if (verificationCode == null || !CODE_PATTERN.matcher(verificationCode).matches()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT);
        }
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

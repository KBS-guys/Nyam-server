package com.nyam.domain.user.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.nyam.domain.user.model.EmailVerificationChallenge;
import com.nyam.domain.user.model.EmailVerificationProof;
import com.nyam.domain.user.repository.EmailVerificationChallengeRepository;
import com.nyam.domain.user.repository.EmailVerificationProofRepository;
import com.nyam.domain.user.repository.UserAccountRepository;
import com.nyam.global.exception.BusinessException;
import com.nyam.global.exception.ErrorCode;

/**
 * 이메일 인증 서비스의 발송 제한, 오입력 커밋 결과와 proof 교체 흐름을 검증합니다.
 */
class EmailVerificationServiceTest {

    private static final Instant NOW = Instant.parse("2026-08-21T00:00:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);
    private static final String DISPLAY_EMAIL = "User+tag@Example.COM";
    private static final String CANONICAL_EMAIL = "user+tag@example.com";
    private static final String CODE = String.format("%06d",
            Math.floorMod("email-verification".hashCode(), 1_000_000));
    private static final byte[] VERIFIER = new byte[32];

    private final EmailVerificationCodeGenerator codeGenerator = mock(EmailVerificationCodeGenerator.class);
    private final EmailVerificationCodeVerifier codeVerifier = mock(EmailVerificationCodeVerifier.class);
    private final VerificationProofGenerator proofGenerator = mock(VerificationProofGenerator.class);
    private final VerificationProofHasher proofHasher = mock(VerificationProofHasher.class);
    private final VerificationMailSender mailSender = mock(VerificationMailSender.class);
    private final EmailVerificationChallengeRepository challengeRepository =
            mock(EmailVerificationChallengeRepository.class);
    private final EmailVerificationProofRepository proofRepository = mock(EmailVerificationProofRepository.class);
    private final UserAccountRepository userRepository = mock(UserAccountRepository.class);
    private EmailVerificationService service;

    /**
     * 실제 이메일 정규화 정책과 모의 외부 협력 객체를 조합한 서비스를 구성합니다.
     */
    @BeforeEach
    void setUp() {
        service = new EmailVerificationService(
                new EmailCanonicalizer(),
                codeGenerator,
                codeVerifier,
                proofGenerator,
                proofHasher,
                mailSender,
                challengeRepository,
                proofRepository,
                userRepository,
                CLOCK);
        when(codeGenerator.generate()).thenReturn(CODE);
        when(codeVerifier.hash(CANONICAL_EMAIL, CODE)).thenReturn(VERIFIER);
    }

    /**
     * 최초 발송이 challenge를 flush한 뒤 메일을 보내고 승인된 만료 시각을 반환하는지 확인합니다.
     */
    @Test
    void storesInitialChallengeBeforeSendingMail() {
        when(userRepository.existsByCanonicalEmail(CANONICAL_EMAIL)).thenReturn(false);
        when(challengeRepository.findByCanonicalEmailForUpdate(CANONICAL_EMAIL))
                .thenReturn(Optional.empty());

        EmailVerificationSendResult result = service.sendCode("  " + DISPLAY_EMAIL + "  ");

        ArgumentCaptor<EmailVerificationChallenge> challengeCaptor =
                ArgumentCaptor.forClass(EmailVerificationChallenge.class);
        verify(challengeRepository).saveAndFlush(challengeCaptor.capture());
        verify(mailSender).send(DISPLAY_EMAIL, CODE);
        assertThat(challengeCaptor.getValue().getCanonicalEmail()).isEqualTo(CANONICAL_EMAIL);
        assertThat(challengeCaptor.getValue().getCodeVerifier()).containsExactly(VERIFIER);
        assertThat(result.codeExpiresAt()).isEqualTo(NOW.plusSeconds(300));
        assertThat(result.resendAvailableAt()).isEqualTo(NOW.plusSeconds(60));
    }

    /**
     * 유효한 현재 코드의 60초 대기시간 안에는 상태 변경과 메일 발송을 모두 차단하는지 확인합니다.
     */
    @Test
    void blocksResendBeforeTheSixtySecondBoundary() {
        EmailVerificationChallenge challenge = activeChallenge();
        when(challengeRepository.findByCanonicalEmailForUpdate(CANONICAL_EMAIL))
                .thenReturn(Optional.of(challenge));

        assertThatThrownBy(() -> service.sendCode(DISPLAY_EMAIL))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode())
                .isEqualTo(ErrorCode.EMAIL_VERIFICATION_SEND_LIMITED);
        verify(challengeRepository, never()).flush();
        verify(mailSender, never()).send(any(), any());
    }

    /**
     * 다섯 번째 불일치가 횟수 5를 남기고 시도 초과 결과를 반환하는지 확인합니다.
     */
    @Test
    void fifthMismatchReturnsLimitAfterRecordingTheCount() {
        EmailVerificationChallenge challenge = activeChallenge();
        for (int index = 0; index < 4; index++) {
            challenge.recordMismatch();
        }
        when(challengeRepository.findByCanonicalEmailForUpdate(CANONICAL_EMAIL))
                .thenReturn(Optional.of(challenge));
        when(codeVerifier.matches(CANONICAL_EMAIL, differentCode(), VERIFIER)).thenReturn(false);

        EmailVerificationConfirmationResult result = service.confirmCode(DISPLAY_EMAIL, differentCode());

        assertThat(result.errorCode()).isEqualTo(ErrorCode.EMAIL_VERIFICATION_ATTEMPTS_EXCEEDED);
        assertThat(challenge.getFailedAttemptCount()).isEqualTo(5);
        verify(challengeRepository).flush();
        verify(proofRepository, never()).saveAndFlush(any());
    }

    /**
     * 만료된 제출은 불일치 횟수를 늘리지 않고 단일 인증 실패로 처리하는지 확인합니다.
     */
    @Test
    void expiredCodeDoesNotIncreaseFailedAttempts() {
        LocalDateTime now = LocalDateTime.ofInstant(NOW, ZoneOffset.UTC);
        EmailVerificationChallenge challenge = new EmailVerificationChallenge(
                CANONICAL_EMAIL, DISPLAY_EMAIL, VERIFIER, now.minusMinutes(5), now);
        when(challengeRepository.findByCanonicalEmailForUpdate(CANONICAL_EMAIL))
                .thenReturn(Optional.of(challenge));

        EmailVerificationConfirmationResult result = service.confirmCode(DISPLAY_EMAIL, CODE);

        assertThat(result.errorCode()).isEqualTo(ErrorCode.EMAIL_VERIFICATION_INVALID);
        assertThat(challenge.getFailedAttemptCount()).isZero();
        verify(codeVerifier, never()).matches(any(), any(), any());
    }

    /**
     * 성공한 코드를 challenge 삭제와 기존 proof 교체 후 새 proof 저장으로 전환하는지 확인합니다.
     */
    @Test
    void successfulConfirmationAtomicallyReplacesProofState() {
        EmailVerificationChallenge challenge = activeChallenge();
        String rawProof = generatedProof();
        byte[] proofHash = new byte[32];
        when(challengeRepository.findByCanonicalEmailForUpdate(CANONICAL_EMAIL))
                .thenReturn(Optional.of(challenge));
        when(codeVerifier.matches(CANONICAL_EMAIL, CODE, VERIFIER)).thenReturn(true);
        when(proofGenerator.generate()).thenReturn(rawProof);
        when(proofHasher.hash(rawProof)).thenReturn(proofHash);
        EmailVerificationProof existing = mock(EmailVerificationProof.class);
        when(proofRepository.findByCanonicalEmailForUpdate(CANONICAL_EMAIL))
                .thenReturn(Optional.of(existing));

        EmailVerificationConfirmationResult result = service.confirmCode(DISPLAY_EMAIL, CODE);

        ArgumentCaptor<EmailVerificationProof> proofCaptor =
                ArgumentCaptor.forClass(EmailVerificationProof.class);
        verify(challengeRepository).delete(challenge);
        verify(proofRepository).delete(existing);
        verify(proofRepository).saveAndFlush(proofCaptor.capture());
        assertThat(proofCaptor.getValue().getCanonicalEmail()).isEqualTo(CANONICAL_EMAIL);
        assertThat(result.verificationProof()).isEqualTo(rawProof);
        assertThat(result.proofExpiresAt()).isEqualTo(NOW.plusSeconds(900));
    }

    /**
     * 현재 시각부터 5분간 유효한 기본 challenge를 생성합니다.
     *
     * @return 테스트용 현재 인증 과제
     */
    private EmailVerificationChallenge activeChallenge() {
        LocalDateTime now = LocalDateTime.ofInstant(NOW, ZoneOffset.UTC);
        return new EmailVerificationChallenge(
                CANONICAL_EMAIL, DISPLAY_EMAIL, VERIFIER, now, now.plusMinutes(5));
    }

    /**
     * 현재 코드와 반드시 다른 유효한 6자리 코드를 계산합니다.
     *
     * @return 불일치 테스트에 사용할 6자리 문자열
     */
    private String differentCode() {
        return String.format("%06d", (Integer.parseInt(CODE) + 1) % 1_000_000);
    }

    /**
     * 고정 문자열을 저장하지 않고 테스트 실행 중 43자 URL-safe proof를 생성합니다.
     *
     * @return 기존 proof 형식을 충족하는 테스트 값
     */
    private String generatedProof() {
        byte[] bytes = new byte[32];
        new SecureRandom().nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}

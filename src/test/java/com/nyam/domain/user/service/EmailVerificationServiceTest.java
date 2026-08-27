package com.nyam.domain.user.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

import com.nyam.domain.user.model.EmailVerificationChallenge;
import com.nyam.domain.user.repository.EmailVerificationChallengeRepository;
import com.nyam.domain.user.repository.UserAccountRepository;
import com.nyam.global.exception.BusinessException;
import com.nyam.global.exception.ErrorCode;

/**
 * 인증번호 발송 제한과 가입 경쟁 재확인 경계를 검증합니다.
 */
class EmailVerificationServiceTest {

    private static final Instant NOW = Instant.parse("2026-08-27T00:00:00Z");
    private static final String DISPLAY_EMAIL = "User@Example.COM";
    private static final String CANONICAL_EMAIL = "user@example.com";
    private static final String CODE = "012345";

    private final EmailVerificationCodeGenerator codeGenerator = mock(EmailVerificationCodeGenerator.class);
    private final VerificationMailSender mailSender = mock(VerificationMailSender.class);
    private final EmailVerificationChallengeRepository challengeRepository =
            mock(EmailVerificationChallengeRepository.class);
    private final UserAccountRepository userRepository = mock(UserAccountRepository.class);
    private final EmailVerificationCodeVerifier codeVerifier = new EmailVerificationCodeVerifier(
            Base64.getEncoder().encodeToString(new byte[32]));
    private final EmailVerificationService service = new EmailVerificationService(
            new EmailCanonicalizer(),
            codeGenerator,
            codeVerifier,
            mailSender,
            challengeRepository,
            userRepository,
            Clock.fixed(NOW, ZoneOffset.UTC));

    @BeforeEach
    void setUp() {
        when(codeGenerator.generate()).thenReturn(CODE);
    }

    @Test
    void firstSendCreatesChallengeAndSendsOneMail() {
        when(challengeRepository.findByCanonicalEmailForUpdate(CANONICAL_EMAIL))
                .thenReturn(Optional.empty());

        service.sendCode(DISPLAY_EMAIL);

        verify(challengeRepository).saveAndFlush(any(EmailVerificationChallenge.class));
        verify(mailSender).send(DISPLAY_EMAIL, CODE);
    }

    @Test
    void registeredEmailAfterChallengeLockPreventsStaleMail() {
        when(userRepository.existsByCanonicalEmail(CANONICAL_EMAIL)).thenReturn(false, true);
        when(challengeRepository.findByCanonicalEmailForUpdate(CANONICAL_EMAIL))
                .thenReturn(Optional.empty());

        assertError(() -> service.sendCode(DISPLAY_EMAIL), ErrorCode.EMAIL_ALREADY_REGISTERED);

        verify(challengeRepository, never()).saveAndFlush(any());
        verify(mailSender, never()).send(any(), any());
    }

    @Test
    void activeChallengeInsideResendDelayReturnsLimitWithoutMail() {
        when(challengeRepository.findByCanonicalEmailForUpdate(CANONICAL_EMAIL))
                .thenReturn(Optional.of(challenge(NOW.minusSeconds(30), NOW.plusSeconds(270))));

        assertError(() -> service.sendCode(DISPLAY_EMAIL), ErrorCode.EMAIL_VERIFICATION_SEND_LIMITED);
        verify(mailSender, never()).send(any(), any());
    }

    @Test
    void concurrentFirstInsertLoserDoesNotSendMail() {
        when(challengeRepository.findByCanonicalEmailForUpdate(CANONICAL_EMAIL))
                .thenReturn(Optional.empty());
        when(challengeRepository.saveAndFlush(any(EmailVerificationChallenge.class)))
                .thenThrow(new DataIntegrityViolationException("primary key"));

        assertError(() -> service.sendCode(DISPLAY_EMAIL), ErrorCode.EMAIL_VERIFICATION_SEND_LIMITED);
        verify(mailSender, never()).send(any(), any());
    }

    @Test
    void expiredChallengeStartsNewSessionAndSendsMail() {
        EmailVerificationChallenge challenge = challenge(NOW.minusSeconds(600), NOW.minusSeconds(1));
        when(challengeRepository.findByCanonicalEmailForUpdate(CANONICAL_EMAIL))
                .thenReturn(Optional.of(challenge));

        service.sendCode(DISPLAY_EMAIL);

        verify(challengeRepository).flush();
        verify(mailSender).send(DISPLAY_EMAIL, CODE);
    }

    private EmailVerificationChallenge challenge(Instant issuedAt, Instant expiresAt) {
        return new EmailVerificationChallenge(
                CANONICAL_EMAIL,
                DISPLAY_EMAIL,
                codeVerifier.hash(CANONICAL_EMAIL, "999999"),
                LocalDateTime.ofInstant(issuedAt, ZoneOffset.UTC),
                LocalDateTime.ofInstant(expiresAt, ZoneOffset.UTC));
    }

    private void assertError(
            org.assertj.core.api.ThrowableAssert.ThrowingCallable callable,
            ErrorCode expected) {
        assertThatThrownBy(callable)
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode())
                .isEqualTo(expected);
    }
}

package com.nyam.domain.user.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.nyam.domain.user.model.EmailVerificationChallenge;
import com.nyam.domain.user.model.UserAccount;
import com.nyam.domain.user.policy.AgePolicy;
import com.nyam.domain.user.policy.ConsentPolicy;
import com.nyam.domain.user.policy.PasswordPolicy;
import com.nyam.domain.user.repository.EmailVerificationChallengeRepository;
import com.nyam.domain.user.repository.LocalCredentialRepository;
import com.nyam.domain.user.repository.UserAccountRepository;
import com.nyam.domain.user.repository.UserConsentRepository;
import com.nyam.global.exception.BusinessException;
import com.nyam.global.exception.ErrorCode;

/**
 * 회원가입의 사전 정책, challenge 소비와 오류 커밋 경계를 검증합니다.
 */
class UserRegistrationServiceTest {

    private static final Instant NOW = Instant.parse("2026-08-27T00:00:00Z");
    private static final String DISPLAY_EMAIL = "User@Example.COM";
    private static final String CANONICAL_EMAIL = "user@example.com";
    private static final String CODE = "012345";
    private static final String PASSWORD = "safe-password";

    private final EmailCanonicalizer canonicalizer = new EmailCanonicalizer();
    private final EmailVerificationCodeVerifier codeVerifier = new EmailVerificationCodeVerifier(
            Base64.getEncoder().encodeToString(new byte[32]));
    private final EmailVerificationChallengeRepository challengeRepository =
            mock(EmailVerificationChallengeRepository.class);
    private final UserAccountRepository userRepository = mock(UserAccountRepository.class);
    private final LocalCredentialRepository credentialRepository = mock(LocalCredentialRepository.class);
    private final UserConsentRepository consentRepository = mock(UserConsentRepository.class);
    private final PasswordEncoder passwordEncoder = mock(PasswordEncoder.class);
    private final UserRegistrationService service = new UserRegistrationService(
            canonicalizer,
            codeVerifier,
            challengeRepository,
            new ConsentPolicy(),
            new AgePolicy(Clock.fixed(NOW, ZoneOffset.UTC)),
            new PasswordPolicy(),
            userRepository,
            credentialRepository,
            consentRepository,
            passwordEncoder,
            Clock.fixed(NOW, ZoneOffset.UTC));

    @BeforeEach
    void setUp() {
        when(passwordEncoder.encode(PASSWORD)).thenReturn("{bcrypt}encoded");
        when(userRepository.saveAndFlush(any(UserAccount.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void successUsesChallengeDisplayEmailAndConsumesChallenge() {
        EmailVerificationChallenge challenge = validChallenge();
        when(challengeRepository.findByCanonicalEmailForUpdate(CANONICAL_EMAIL))
                .thenReturn(Optional.of(challenge));

        RegisterUserResult result = service.register(command("user@example.com", CODE));

        assertThat(result.errorCode()).isNull();
        assertThat(result.displayEmail()).isEqualTo(DISPLAY_EMAIL);
        verify(passwordEncoder).encode(PASSWORD);
        verify(userRepository).saveAndFlush(any(UserAccount.class));
        verify(credentialRepository).save(any());
        verify(consentRepository).saveAll(any());
        verify(challengeRepository).delete(challenge);
        verify(challengeRepository).flush();
    }

    @Test
    void wrongCodeReturnsCommittedFailureWithoutCreatingAccount() {
        EmailVerificationChallenge challenge = validChallenge();
        when(challengeRepository.findByCanonicalEmailForUpdate(CANONICAL_EMAIL))
                .thenReturn(Optional.of(challenge));

        RegisterUserResult result = service.register(command(DISPLAY_EMAIL, "999999"));

        assertThat(result.errorCode()).isEqualTo(ErrorCode.EMAIL_VERIFICATION_INVALID);
        assertThat(challenge.getFailedAttemptCount()).isEqualTo(1);
        verify(challengeRepository).flush();
        verify(userRepository, never()).saveAndFlush(any());
    }

    @Test
    void fifthWrongCodeReturnsAttemptsExceeded() {
        EmailVerificationChallenge challenge = validChallenge();
        for (int attempt = 0; attempt < 4; attempt++) {
            challenge.recordMismatch();
        }
        when(challengeRepository.findByCanonicalEmailForUpdate(CANONICAL_EMAIL))
                .thenReturn(Optional.of(challenge));

        RegisterUserResult result = service.register(command(DISPLAY_EMAIL, "999999"));

        assertThat(result.errorCode()).isEqualTo(ErrorCode.EMAIL_VERIFICATION_ATTEMPTS_EXCEEDED);
        assertThat(challenge.getFailedAttemptCount()).isEqualTo(5);
    }

    @Test
    void missingChallengeReturnsVerificationInvalid() {
        when(challengeRepository.findByCanonicalEmailForUpdate(CANONICAL_EMAIL))
                .thenReturn(Optional.empty());

        assertThat(service.register(command(DISPLAY_EMAIL, CODE)).errorCode())
                .isEqualTo(ErrorCode.EMAIL_VERIFICATION_INVALID);
        verifyNoInteractions(passwordEncoder);
    }

    @Test
    void invalidConsentStopsBeforeChallengeLookup() {
        RegisterUserCommand command = new RegisterUserCommand(
                DISPLAY_EMAIL, CODE, PASSWORD, LocalDate.of(2000, 1, 1), true, false, true);

        assertThatThrownBy(() -> service.register(command))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode())
                .isEqualTo(ErrorCode.REQUIRED_CONSENT_MISSING);
        verifyNoInteractions(challengeRepository);
    }

    @Test
    void duplicateEmailDoesNotConsumeChallenge() {
        EmailVerificationChallenge challenge = validChallenge();
        when(challengeRepository.findByCanonicalEmailForUpdate(CANONICAL_EMAIL))
                .thenReturn(Optional.of(challenge));
        when(userRepository.existsByCanonicalEmail(CANONICAL_EMAIL)).thenReturn(true);

        assertThatThrownBy(() -> service.register(command(DISPLAY_EMAIL, CODE)))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode())
                .isEqualTo(ErrorCode.EMAIL_ALREADY_REGISTERED);
        verify(challengeRepository, never()).delete(any());
    }

    @Test
    void databaseIntegrityFailureDoesNotDeleteChallenge() {
        EmailVerificationChallenge challenge = validChallenge();
        when(challengeRepository.findByCanonicalEmailForUpdate(CANONICAL_EMAIL))
                .thenReturn(Optional.of(challenge));
        when(userRepository.saveAndFlush(any(UserAccount.class)))
                .thenThrow(new DataIntegrityViolationException("duplicate"));

        assertThatThrownBy(() -> service.register(command(DISPLAY_EMAIL, CODE)))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode())
                .isEqualTo(ErrorCode.EMAIL_ALREADY_REGISTERED);
        verify(challengeRepository, never()).delete(any());
    }

    private EmailVerificationChallenge validChallenge() {
        LocalDateTime now = LocalDateTime.ofInstant(NOW, ZoneOffset.UTC);
        return new EmailVerificationChallenge(
                CANONICAL_EMAIL,
                DISPLAY_EMAIL,
                codeVerifier.hash(CANONICAL_EMAIL, CODE),
                now.minusSeconds(60),
                now.plusSeconds(240));
    }

    private RegisterUserCommand command(String email, String code) {
        return new RegisterUserCommand(
                email, code, PASSWORD, LocalDate.of(2000, 1, 1), true, true, true);
    }
}

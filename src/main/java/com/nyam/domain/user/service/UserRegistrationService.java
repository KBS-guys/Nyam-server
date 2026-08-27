package com.nyam.domain.user.service;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.nyam.domain.user.model.EmailVerificationChallenge;
import com.nyam.domain.user.model.LocalCredential;
import com.nyam.domain.user.model.UserAccount;
import com.nyam.domain.user.model.UserConsent;
import com.nyam.domain.user.policy.AgePolicy;
import com.nyam.domain.user.policy.ConsentAgreement;
import com.nyam.domain.user.policy.ConsentPolicy;
import com.nyam.domain.user.policy.PasswordPolicy;
import com.nyam.domain.user.repository.EmailVerificationChallengeRepository;
import com.nyam.domain.user.repository.LocalCredentialRepository;
import com.nyam.domain.user.repository.UserAccountRepository;
import com.nyam.domain.user.repository.UserConsentRepository;
import com.nyam.global.exception.BusinessException;
import com.nyam.global.exception.ErrorCode;

/**
 * 현재 이메일 challenge를 직접 검증하고 로컬 계정 생성을 원자적으로 완료합니다.
 */
@Service
public class UserRegistrationService {

    private static final int MAX_FAILED_ATTEMPTS = 5;

    private final EmailCanonicalizer emailCanonicalizer;
    private final EmailVerificationCodeVerifier codeVerifier;
    private final EmailVerificationChallengeRepository challengeRepository;
    private final ConsentPolicy consentPolicy;
    private final AgePolicy agePolicy;
    private final PasswordPolicy passwordPolicy;
    private final UserAccountRepository userRepository;
    private final LocalCredentialRepository credentialRepository;
    private final UserConsentRepository consentRepository;
    private final PasswordEncoder passwordEncoder;
    private final Clock clock;

    /** 회원가입 검증, 잠금과 원자적 저장에 필요한 협력 객체를 주입받습니다. */
    UserRegistrationService(
            EmailCanonicalizer emailCanonicalizer,
            EmailVerificationCodeVerifier codeVerifier,
            EmailVerificationChallengeRepository challengeRepository,
            ConsentPolicy consentPolicy,
            AgePolicy agePolicy,
            PasswordPolicy passwordPolicy,
            UserAccountRepository userRepository,
            LocalCredentialRepository credentialRepository,
            UserConsentRepository consentRepository,
            PasswordEncoder passwordEncoder,
            Clock clock) {
        this.emailCanonicalizer = emailCanonicalizer;
        this.codeVerifier = codeVerifier;
        this.challengeRepository = challengeRepository;
        this.consentPolicy = consentPolicy;
        this.agePolicy = agePolicy;
        this.passwordPolicy = passwordPolicy;
        this.userRepository = userRepository;
        this.credentialRepository = credentialRepository;
        this.consentRepository = consentRepository;
        this.passwordEncoder = passwordEncoder;
        this.clock = clock;
    }

    /**
     * 기본 가입 정책을 먼저 검증한 뒤 challenge 잠금, 번호 확인, 계정 저장과 challenge 소비를 처리합니다.
     *
     * <p>인증번호 불일치는 횟수를 커밋할 수 있도록 결과로 반환합니다. 성공 저장 중 실패하면 계정 데이터와
     * challenge 삭제가 모두 롤백됩니다.</p>
     *
     * @param command 이메일 인증번호와 가입 정보를 담은 명령
     * @return 성공 표시 이메일 또는 커밋된 인증 실패 코드
     */
    @Transactional
    public RegisterUserResult register(RegisterUserCommand command) {
        NormalizedEmailAddress submittedEmail = emailCanonicalizer.normalize(command.email());
        codeVerifier.requireValidFormat(command.verificationCode());
        List<ConsentAgreement> consents = consentPolicy.resolveRequired(
                command.termsAgreed(),
                command.personalInformationAgreed(),
                command.healthInformationAgreed());
        agePolicy.requireEligible(command.birthDate());
        String password = passwordPolicy.validate(command.password());

        LocalDateTime now = LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC);
        EmailVerificationChallenge challenge = challengeRepository
                .findByCanonicalEmailForUpdate(submittedEmail.canonicalEmail())
                .orElse(null);
        if (challenge == null || !now.isBefore(challenge.getExpiresAt())) {
            return RegisterUserResult.failure(ErrorCode.EMAIL_VERIFICATION_INVALID);
        }
        if (challenge.getFailedAttemptCount() >= MAX_FAILED_ATTEMPTS) {
            return RegisterUserResult.failure(ErrorCode.EMAIL_VERIFICATION_ATTEMPTS_EXCEEDED);
        }
        if (!codeVerifier.matches(challenge.getCanonicalEmail(), command.verificationCode(),
                challenge.getCodeVerifier())) {
            challenge.recordMismatch();
            challengeRepository.flush();
            ErrorCode errorCode = challenge.getFailedAttemptCount() >= MAX_FAILED_ATTEMPTS
                    ? ErrorCode.EMAIL_VERIFICATION_ATTEMPTS_EXCEEDED
                    : ErrorCode.EMAIL_VERIFICATION_INVALID;
            return RegisterUserResult.failure(errorCode);
        }
        if (userRepository.existsByCanonicalEmail(challenge.getCanonicalEmail())) {
            throw new BusinessException(ErrorCode.EMAIL_ALREADY_REGISTERED);
        }

        String encodedPassword = passwordEncoder.encode(password);
        UserAccount user = saveUser(challenge, command, now);
        credentialRepository.save(new LocalCredential(user, encodedPassword, now));
        consentRepository.saveAll(consents.stream()
                .map(consent -> new UserConsent(user, consent.type(), consent.version(), now))
                .toList());
        challengeRepository.delete(challenge);
        challengeRepository.flush();
        return RegisterUserResult.success(challenge.getDisplayEmail());
    }

    private UserAccount saveUser(
            EmailVerificationChallenge challenge,
            RegisterUserCommand command,
            LocalDateTime now) {
        try {
            return userRepository.saveAndFlush(new UserAccount(
                    challenge.getDisplayEmail(),
                    challenge.getCanonicalEmail(),
                    command.birthDate(),
                    now));
        } catch (DataIntegrityViolationException exception) {
            throw new BusinessException(ErrorCode.EMAIL_ALREADY_REGISTERED);
        }
    }
}

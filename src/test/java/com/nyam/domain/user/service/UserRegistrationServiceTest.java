package com.nyam.domain.user.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.nyam.domain.user.model.ConsentType;
import com.nyam.domain.user.model.EmailVerificationProof;
import com.nyam.domain.user.model.LocalCredential;
import com.nyam.domain.user.model.UserAccount;
import com.nyam.domain.user.model.UserConsent;
import com.nyam.domain.user.policy.AgePolicy;
import com.nyam.domain.user.policy.ConsentAgreement;
import com.nyam.domain.user.policy.ConsentPolicy;
import com.nyam.domain.user.policy.PasswordPolicy;
import com.nyam.domain.user.repository.EmailVerificationProofRepository;
import com.nyam.domain.user.repository.LocalCredentialRepository;
import com.nyam.domain.user.repository.UserAccountRepository;
import com.nyam.domain.user.repository.UserConsentRepository;
import com.nyam.global.exception.BusinessException;
import com.nyam.global.exception.ErrorCode;

/**
 * 단일 회원가입 서비스의 검증, 저장 순서와 공개 오류 변환을 검증합니다.
 */
class UserRegistrationServiceTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-08-14T00:00:00Z"), ZoneOffset.UTC);
    private static final String RAW_PROOF = "A".repeat(43);
    private static final String PASSWORD = "password";

    private final VerificationProofHasher hasher = new VerificationProofHasher();
    private final EmailVerificationProofRepository proofRepository = mock(EmailVerificationProofRepository.class);
    private final ConsentPolicy consentPolicy = mock(ConsentPolicy.class);
    private final AgePolicy agePolicy = mock(AgePolicy.class);
    private final PasswordPolicy passwordPolicy = mock(PasswordPolicy.class);
    private final UserAccountRepository userRepository = mock(UserAccountRepository.class);
    private final LocalCredentialRepository credentialRepository = mock(LocalCredentialRepository.class);
    private final UserConsentRepository consentRepository = mock(UserConsentRepository.class);
    private final PasswordEncoder passwordEncoder = mock(PasswordEncoder.class);
    private UserRegistrationService service;

    /**
     * 각 테스트가 동일한 협력 객체 조합으로 회원가입 서비스를 사용하도록 초기화합니다.
     */
    @BeforeEach
    void setUp() {
        service = new UserRegistrationService(
                hasher,
                proofRepository,
                consentPolicy,
                agePolicy,
                passwordPolicy,
                userRepository,
                credentialRepository,
                consentRepository,
                passwordEncoder,
                CLOCK);
    }

    /**
     * 잠근 증명의 이메일로 사용자·자격 증명·동의를 저장한 뒤 증명을 소비하는지 확인합니다.
     */
    @Test
    void registersProofBoundIdentityInOneStraightforwardFlow() {
        byte[] proofHash = hasher.hash(RAW_PROOF);
        List<ConsentAgreement> consents = validConsents();
        when(proofRepository.findByProofHashForUpdate(proofHash)).thenReturn(Optional.of(validProof(proofHash)));
        when(consentPolicy.validate(consents)).thenReturn(consents);
        when(passwordPolicy.validate(PASSWORD)).thenReturn(PASSWORD);
        when(userRepository.existsByCanonicalEmail("user@example.com")).thenReturn(false);
        when(passwordEncoder.encode(PASSWORD)).thenReturn("{bcrypt}encoded");
        when(userRepository.saveAndFlush(any(UserAccount.class))).thenAnswer(invocation -> invocation.getArgument(0));

        String result = service.register(command(consents));

        assertThat(result).isEqualTo("User@Example.COM");
        InOrder order = inOrder(proofRepository, consentPolicy, agePolicy, passwordPolicy,
                userRepository, passwordEncoder, credentialRepository, consentRepository);
        order.verify(proofRepository).findByProofHashForUpdate(proofHash);
        order.verify(consentPolicy).validate(consents);
        order.verify(agePolicy).requireEligible(LocalDate.of(2000, 1, 1));
        order.verify(passwordPolicy).validate(PASSWORD);
        order.verify(userRepository).existsByCanonicalEmail("user@example.com");
        order.verify(passwordEncoder).encode(PASSWORD);
        order.verify(userRepository).saveAndFlush(any(UserAccount.class));
        order.verify(credentialRepository).save(any(LocalCredential.class));
        order.verify(consentRepository).saveAll(any());
        order.verify(proofRepository).delete(any(EmailVerificationProof.class));
        order.verify(proofRepository).flush();
    }

    /**
     * 존재하지 않는 증명이 정책 검사와 모든 데이터 쓰기를 중단하는지 확인합니다.
     */
    @Test
    void invalidProofStopsBeforeValidationAndWrites() {
        when(proofRepository.findByProofHashForUpdate(any())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.register(command(validConsents())))
                .isInstanceOfSatisfying(BusinessException.class,
                        exception -> assertThat(exception.getErrorCode())
                                .isEqualTo(ErrorCode.EMAIL_VERIFICATION_INVALID));

        verify(consentPolicy, never()).validate(any());
        verify(userRepository, never()).saveAndFlush(any());
        verify(proofRepository, never()).delete(any());
    }

    /**
     * 비밀번호 정책 실패가 인코딩과 모든 데이터 쓰기를 중단하는지 확인합니다.
     */
    @Test
    void invalidPasswordStopsBeforeEncodingAndWrites() {
        byte[] proofHash = hasher.hash(RAW_PROOF);
        List<ConsentAgreement> consents = validConsents();
        when(proofRepository.findByProofHashForUpdate(proofHash)).thenReturn(Optional.of(validProof(proofHash)));
        when(consentPolicy.validate(consents)).thenReturn(consents);
        when(passwordPolicy.validate(PASSWORD))
                .thenThrow(new BusinessException(ErrorCode.PASSWORD_POLICY_VIOLATION));

        assertThatThrownBy(() -> service.register(command(consents)))
                .isInstanceOfSatisfying(BusinessException.class,
                        exception -> assertThat(exception.getErrorCode())
                                .isEqualTo(ErrorCode.PASSWORD_POLICY_VIOLATION));

        verify(passwordEncoder, never()).encode(anyString());
        verify(userRepository, never()).saveAndFlush(any());
        verify(proofRepository, never()).delete(any());
    }

    /**
     * 이미 가입된 canonical email이 인코딩과 저장 전에 공개 중복 오류로 반환되는지 확인합니다.
     */
    @Test
    void duplicateEmailStopsBeforeEncodingAndWrites() {
        byte[] proofHash = hasher.hash(RAW_PROOF);
        List<ConsentAgreement> consents = validConsents();
        when(proofRepository.findByProofHashForUpdate(proofHash)).thenReturn(Optional.of(validProof(proofHash)));
        when(consentPolicy.validate(consents)).thenReturn(consents);
        when(passwordPolicy.validate(PASSWORD)).thenReturn(PASSWORD);
        when(userRepository.existsByCanonicalEmail("user@example.com")).thenReturn(true);

        assertThatThrownBy(() -> service.register(command(consents)))
                .isInstanceOfSatisfying(BusinessException.class,
                        exception -> assertThat(exception.getErrorCode())
                                .isEqualTo(ErrorCode.EMAIL_ALREADY_REGISTERED));

        verify(passwordEncoder, never()).encode(anyString());
        verify(userRepository, never()).saveAndFlush(any());
        verify(proofRepository, never()).delete(any());
    }

    /**
     * 사용자 삽입 시 발생한 UNIQUE 경쟁 실패를 중복 이메일 오류로 변환하고 증명을 삭제하지 않는지 확인합니다.
     */
    @Test
    void uniqueConstraintRaceMapsToDuplicateEmailWithoutDeletingProof() {
        byte[] proofHash = hasher.hash(RAW_PROOF);
        List<ConsentAgreement> consents = validConsents();
        when(proofRepository.findByProofHashForUpdate(proofHash)).thenReturn(Optional.of(validProof(proofHash)));
        when(consentPolicy.validate(consents)).thenReturn(consents);
        when(passwordPolicy.validate(PASSWORD)).thenReturn(PASSWORD);
        when(userRepository.existsByCanonicalEmail("user@example.com")).thenReturn(false);
        when(passwordEncoder.encode(PASSWORD)).thenReturn("{bcrypt}encoded");
        when(userRepository.saveAndFlush(any(UserAccount.class)))
                .thenThrow(new DataIntegrityViolationException("duplicate"));

        assertThatThrownBy(() -> service.register(command(consents)))
                .isInstanceOfSatisfying(BusinessException.class,
                        exception -> assertThat(exception.getErrorCode())
                                .isEqualTo(ErrorCode.EMAIL_ALREADY_REGISTERED));

        verify(credentialRepository, never()).save(any());
        verify(consentRepository, never()).saveAll(any());
        verify(proofRepository, never()).delete(any());
    }

    /**
     * 지정한 동의 목록으로 표준 회원가입 명령을 생성합니다.
     *
     * @param consents 명령에 포함할 동의 목록
     * @return 테스트용 회원가입 명령
     */
    private RegisterUserCommand command(List<ConsentAgreement> consents) {
        return new RegisterUserCommand(RAW_PROOF, PASSWORD, LocalDate.of(2000, 1, 1), consents);
    }

    /**
     * 현재 버전의 필수 동의 세 종류를 생성합니다.
     *
     * @return 검증을 통과해야 하는 동의 목록
     */
    private List<ConsentAgreement> validConsents() {
        return List.of(
                new ConsentAgreement(ConsentType.TERMS, "1.0"),
                new ConsentAgreement(ConsentType.PERSONAL_INFORMATION, "1.0"),
                new ConsentAgreement(ConsentType.HEALTH_INFORMATION, "1.0"));
    }

    /**
     * 고정 시각 기준으로 아직 만료되지 않은 이메일 인증 증명을 생성합니다.
     *
     * @param proofHash 테스트 원문 증명의 SHA-256 해시
     * @return 원본 표기와 canonical email이 결합된 유효한 증명
     */
    private EmailVerificationProof validProof(byte[] proofHash) {
        return new EmailVerificationProof(
                proofHash,
                "User@Example.COM",
                "user@example.com",
                LocalDateTime.of(2026, 8, 13, 23, 50),
                LocalDateTime.of(2026, 8, 14, 0, 10));
    }
}

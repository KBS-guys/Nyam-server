package com.nyam.domain.user.service;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
 * 이메일 인증 증명을 소비하고 로컬 계정 데이터를 하나의 트랜잭션으로 생성합니다.
 */
@Service
public class UserRegistrationService {

    private final VerificationProofHasher proofHasher;
    private final EmailVerificationProofRepository proofRepository;
    private final ConsentPolicy consentPolicy;
    private final AgePolicy agePolicy;
    private final PasswordPolicy passwordPolicy;
    private final UserAccountRepository userRepository;
    private final LocalCredentialRepository credentialRepository;
    private final UserConsentRepository consentRepository;
    private final PasswordEncoder passwordEncoder;
    private final Clock clock;

    /**
     * 회원가입 검증과 원자적 저장에 필요한 협력 객체를 주입받습니다.
     *
     * @param proofHasher 원문 증명 형식 검사 및 해시 생성기
     * @param proofRepository 인증 증명 잠금 및 소비 저장소
     * @param consentPolicy 필수 동의 검증 정책
     * @param agePolicy 최소 연령 검증 정책
     * @param passwordPolicy 기본 비밀번호 길이 검증 정책
     * @param userRepository 사용자 저장 및 이메일 중복 확인 저장소
     * @param credentialRepository 로컬 비밀번호 해시 저장소
     * @param consentRepository 사용자 동의 저장소
     * @param passwordEncoder 평문 비밀번호 인코더
     * @param clock 가입 처리 기준 시각 공급자
     */
    public UserRegistrationService(
            VerificationProofHasher proofHasher,
            EmailVerificationProofRepository proofRepository,
            ConsentPolicy consentPolicy,
            AgePolicy agePolicy,
            PasswordPolicy passwordPolicy,
            UserAccountRepository userRepository,
            LocalCredentialRepository credentialRepository,
            UserConsentRepository consentRepository,
            PasswordEncoder passwordEncoder,
            Clock clock) {
        this.proofHasher = proofHasher;
        this.proofRepository = proofRepository;
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
     * 일회성 인증 증명을 잠근 뒤 사용자·자격 증명·동의를 저장하고 증명을 소비합니다.
     *
     * <p>어느 저장 단계에서든 실패하면 사용자 데이터와 증명 삭제가 모두 롤백됩니다.</p>
     *
     * @param command 웹 계층에서 전달된 최종 회원가입 명령
     * @return 인증 증명에 결합되어 있던 원본 표기 이메일
     * @throws BusinessException 인증 증명, 연령, 동의, 비밀번호 또는 이메일 중복 규칙을 충족하지 못한 경우
     */
    @Transactional
    public String register(RegisterUserCommand command) {
        LocalDateTime now = LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC);
        byte[] proofHash = proofHasher.hash(command.verificationProof());
        EmailVerificationProof proof = proofRepository.findByProofHashForUpdate(proofHash)
                .filter(candidate -> candidate.getExpiresAt().isAfter(now))
                .orElseThrow(() -> new BusinessException(ErrorCode.EMAIL_VERIFICATION_INVALID));

        List<ConsentAgreement> consents = consentPolicy.validate(command.consents());
        agePolicy.requireEligible(command.birthDate());
        String password = passwordPolicy.validate(command.password());
        if (userRepository.existsByCanonicalEmail(proof.getCanonicalEmail())) {
            throw new BusinessException(ErrorCode.EMAIL_ALREADY_REGISTERED);
        }

        String encodedPassword = passwordEncoder.encode(password);
        UserAccount user = saveUser(proof, command, now);
        credentialRepository.save(new LocalCredential(user, encodedPassword, now));
        consentRepository.saveAll(consents.stream()
                .map(consent -> new UserConsent(user, consent.type(), consent.version(), now))
                .toList());
        proofRepository.delete(proof);
        proofRepository.flush();
        return user.getDisplayEmail();
    }

    /**
     * 검증된 인증 증명과 가입 명령으로 사용자를 저장하고 데이터베이스 중복을 공개 오류로 변환합니다.
     *
     * <p>이 삽입 시점의 데이터는 애플리케이션과 증명 테이블의 제약을 이미 통과했으므로,
     * 사용자 삽입 무결성 실패는 canonical email UNIQUE 제약의 최종 경쟁 방어로 취급합니다.</p>
     *
     * @param proof 잠금과 만료 검증을 통과한 이메일 인증 증명
     * @param command 생년월일을 포함한 최종 회원가입 명령
     * @param now 사용자 생성 시각
     * @return 데이터베이스에 저장된 신규 사용자
     * @throws BusinessException 동일한 canonical email이 데이터베이스 UNIQUE 제약에서 확인된 경우
     */
    private UserAccount saveUser(EmailVerificationProof proof, RegisterUserCommand command, LocalDateTime now) {
        try {
            return userRepository.saveAndFlush(new UserAccount(
                    proof.getDisplayEmail(), proof.getCanonicalEmail(), command.birthDate(), now));
        } catch (DataIntegrityViolationException exception) {
            throw new BusinessException(ErrorCode.EMAIL_ALREADY_REGISTERED);
        }
    }
}

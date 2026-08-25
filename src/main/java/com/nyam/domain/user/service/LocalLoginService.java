package com.nyam.domain.user.service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.nyam.domain.user.model.LocalCredential;
import com.nyam.domain.user.model.RefreshToken;
import com.nyam.domain.user.model.UserAccount;
import com.nyam.domain.user.repository.LocalCredentialRepository;
import com.nyam.domain.user.repository.RefreshTokenRepository;
import com.nyam.domain.user.repository.UserAccountRepository;
import com.nyam.global.exception.BusinessException;
import com.nyam.global.exception.ErrorCode;

/**
 * 로컬 자격 증명 로그인, Refresh Token 회전, 로그아웃 폐기와 현재 사용자 조회를 수행합니다.
 */
@Service
public class LocalLoginService {

    private static final Duration REFRESH_TOKEN_LIFETIME = Duration.ofDays(30);
    private static final long REFRESH_TOKEN_SECONDS = REFRESH_TOKEN_LIFETIME.toSeconds();

    private final EmailCanonicalizer emailCanonicalizer;
    private final UserAccountRepository userRepository;
    private final LocalCredentialRepository credentialRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final AccessTokenIssuer accessTokenIssuer;
    private final RefreshTokenCodec refreshTokenCodec;
    private final Clock clock;
    private final String dummyPasswordHash;

    /**
     * 로그인과 토큰 생명주기에 필요한 정책 및 저장소를 주입받고 타이밍 완화용 해시를 한 번 생성합니다.
     *
     * @param emailCanonicalizer 기존 ASCII 이메일 정규화 정책
     * @param userRepository 사용자 계정 조회 저장소
     * @param credentialRepository 로컬 비밀번호 자격 증명 저장소
     * @param refreshTokenRepository Refresh Token 서버 상태 저장소
     * @param passwordEncoder 실제 및 더미 BCrypt 검증기
     * @param accessTokenIssuer Access Token 발급기
     * @param refreshTokenCodec Refresh Token 생성 및 해시 도구
     * @param clock 인증 시간 계산용 UTC 시계
     */
    public LocalLoginService(
            EmailCanonicalizer emailCanonicalizer,
            UserAccountRepository userRepository,
            LocalCredentialRepository credentialRepository,
            RefreshTokenRepository refreshTokenRepository,
            PasswordEncoder passwordEncoder,
            AccessTokenIssuer accessTokenIssuer,
            RefreshTokenCodec refreshTokenCodec,
            Clock clock) {
        this.emailCanonicalizer = emailCanonicalizer;
        this.userRepository = userRepository;
        this.credentialRepository = credentialRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.passwordEncoder = passwordEncoder;
        this.accessTokenIssuer = accessTokenIssuer;
        this.refreshTokenCodec = refreshTokenCodec;
        this.clock = clock;
        this.dummyPasswordHash = passwordEncoder.encode(UUID.randomUUID().toString());
    }

    /**
     * 이메일과 비밀번호를 한 번 검증하고 새 Access/Refresh Token과 서버 상태를 생성합니다.
     *
     * @param submittedEmail 사용자가 제출한 이메일
     * @param submittedPassword 변경하지 않은 사용자의 평문 비밀번호
     * @return 응답 본문과 쿠키를 구성할 발급 결과
     * @throws BusinessException 사용자·자격 증명·비밀번호 중 하나라도 일치하지 않는 경우
     */
    @Transactional
    public IssuedAuthentication login(String submittedEmail, String submittedPassword) {
        NormalizedEmailAddress email = emailCanonicalizer.normalize(submittedEmail);
        Optional<UserAccount> userCandidate = userRepository.findByCanonicalEmail(email.canonicalEmail());
        Optional<LocalCredential> credentialCandidate = userCandidate
                .flatMap(user -> credentialRepository.findById(user.getId()));
        String hashToCheck = credentialCandidate.map(LocalCredential::getPasswordHash)
                .orElse(dummyPasswordHash);
        boolean passwordMatches = passwordEncoder.matches(submittedPassword, hashToCheck);
        if (userCandidate.isEmpty() || credentialCandidate.isEmpty() || !passwordMatches) {
            throw new BusinessException(ErrorCode.LOGIN_FAILED);
        }

        UserAccount user = userCandidate.orElseThrow();
        Instant nowInstant = clock.instant();
        LocalDateTime now = LocalDateTime.ofInstant(nowInstant, ZoneOffset.UTC);
        LocalDateTime expiresAt = now.plus(REFRESH_TOKEN_LIFETIME);
        String refreshToken = refreshTokenCodec.generate();
        byte[] refreshHash = refreshTokenCodec.hashIfValid(refreshToken).orElseThrow();
        String accessToken = accessTokenIssuer.issue(user.getId(), nowInstant);
        refreshTokenRepository.saveCurrent(user.getId(), refreshHash, now, expiresAt);
        return new IssuedAuthentication(accessToken, refreshToken, REFRESH_TOKEN_SECONDS);
    }

    /**
     * 현재 Refresh Token 후보를 조회한 뒤 조건부 갱신으로 한 요청만 회전에 성공시킵니다.
     *
     * @param submittedRefreshToken HttpOnly 쿠키에서 전달된 Refresh Token 원문
     * @return 새 Access Token과 회전된 Refresh Token 발급 결과
     * @throws BusinessException 토큰 형식, 만료, 현재 상태 또는 동시 회전 조건을 충족하지 못한 경우
     */
    @Transactional
    public IssuedAuthentication refresh(String submittedRefreshToken) {
        byte[] oldHash = refreshTokenCodec.hashIfValid(submittedRefreshToken)
                .orElseThrow(() -> new BusinessException(ErrorCode.REFRESH_TOKEN_INVALID));
        RefreshToken candidate = refreshTokenRepository.findByTokenHash(oldHash)
                .orElseThrow(() -> new BusinessException(ErrorCode.REFRESH_TOKEN_INVALID));
        Instant nowInstant = clock.instant();
        LocalDateTime now = LocalDateTime.ofInstant(nowInstant, ZoneOffset.UTC);
        long remainingSeconds = Duration.between(now, candidate.getExpiresAt()).getSeconds();
        if (remainingSeconds <= 0) {
            throw new BusinessException(ErrorCode.REFRESH_TOKEN_INVALID);
        }

        String newRefreshToken = refreshTokenCodec.generate();
        byte[] newHash = refreshTokenCodec.hashIfValid(newRefreshToken).orElseThrow();
        String newAccessToken = accessTokenIssuer.issue(candidate.getUserId(), nowInstant);
        int updated = refreshTokenRepository.rotate(
                candidate.getUserId(), oldHash, newHash, now, now.plusSeconds(1));
        if (updated != 1) {
            throw new BusinessException(ErrorCode.REFRESH_TOKEN_INVALID);
        }
        return new IssuedAuthentication(newAccessToken, newRefreshToken, remainingSeconds);
    }

    /**
     * 제출 쿠키가 유효한 형식이면 만료 여부와 관계없이 일치하는 서버 상태를 삭제합니다.
     *
     * @param submittedRefreshToken 선택적으로 전달된 Refresh Token 원문
     */
    @Transactional
    public void logout(String submittedRefreshToken) {
        refreshTokenCodec.hashIfValid(submittedRefreshToken)
                .ifPresent(refreshTokenRepository::deleteByTokenHashValue);
    }

    /**
     * 인증 주체 식별자로 현재 사용자를 조회하고 공개 이메일만 반환합니다.
     *
     * @param authenticatedUserId SecurityContext에서 파생한 내부 사용자 식별자
     * @return 현재 사용자의 표기 이메일
     * @throws BusinessException 토큰 주체에 대응하는 사용자가 더 이상 존재하지 않는 경우
     */
    @Transactional(readOnly = true)
    public CurrentUserResult currentUser(long authenticatedUserId) {
        UserAccount user = userRepository.findById(authenticatedUserId)
                .orElseThrow(() -> new BusinessException(ErrorCode.UNAUTHORIZED));
        return new CurrentUserResult(user.getDisplayEmail());
    }

}

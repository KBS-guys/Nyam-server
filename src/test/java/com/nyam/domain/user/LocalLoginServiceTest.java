package com.nyam.domain.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.nyam.domain.user.model.LocalCredential;
import com.nyam.domain.user.model.RefreshToken;
import com.nyam.domain.user.model.UserAccount;
import com.nyam.domain.user.repository.LocalCredentialRepository;
import com.nyam.domain.user.repository.RefreshTokenRepository;
import com.nyam.domain.user.repository.UserAccountRepository;
import com.nyam.domain.user.service.AccessTokenIssuer;
import com.nyam.domain.user.service.EmailCanonicalizer;
import com.nyam.domain.user.service.IssuedAuthentication;
import com.nyam.domain.user.service.LocalLoginService;
import com.nyam.domain.user.service.NormalizedEmailAddress;
import com.nyam.domain.user.service.RefreshTokenCodec;
import com.nyam.global.exception.BusinessException;
import com.nyam.global.exception.ErrorCode;

/**
 * 로컬 로그인 서비스의 자격 증명 은닉, 고정 만료 회전과 멱등 폐기 규칙을 검증합니다.
 */
@ExtendWith(MockitoExtension.class)
class LocalLoginServiceTest {

    private static final Instant NOW = Instant.parse("2026-08-25T00:00:00Z");
    private static final String OLD_REFRESH = "A".repeat(43);
    private static final String NEW_REFRESH = "B".repeat(43);
    private static final byte[] OLD_HASH = new byte[32];
    private static final byte[] NEW_HASH = filledHash((byte) 1);

    @Mock
    EmailCanonicalizer emailCanonicalizer;
    @Mock
    UserAccountRepository userRepository;
    @Mock
    LocalCredentialRepository credentialRepository;
    @Mock
    RefreshTokenRepository refreshTokenRepository;
    @Mock
    PasswordEncoder passwordEncoder;
    @Mock
    AccessTokenIssuer accessTokenIssuer;
    @Mock
    RefreshTokenCodec refreshTokenCodec;
    @Mock
    Clock clock;

    LocalLoginService loginService;

    /**
     * 더미 BCrypt 값이 생성되는 서비스 인스턴스를 고정 시계로 구성합니다.
     */
    @BeforeEach
    void setUp() {
        when(passwordEncoder.encode(any())).thenReturn("{bcrypt}dummy-hash");
        loginService = new LocalLoginService(
                emailCanonicalizer,
                userRepository,
                credentialRepository,
                refreshTokenRepository,
                passwordEncoder,
                accessTokenIssuer,
                refreshTokenCodec,
                clock);
    }

    /**
     * 없는 계정도 시작 시 만든 더미 해시로 비밀번호 비교를 정확히 한 번 수행하는지 확인합니다.
     */
    @Test
    void missingUserUsesOneDummyPasswordComparison() {
        when(emailCanonicalizer.normalize("missing@example.com"))
                .thenReturn(new NormalizedEmailAddress("missing@example.com", "missing@example.com"));
        when(userRepository.findByCanonicalEmail("missing@example.com")).thenReturn(Optional.empty());
        when(passwordEncoder.matches("submitted-password", "{bcrypt}dummy-hash")).thenReturn(false);

        assertThatThrownBy(() -> loginService.login("missing@example.com", "submitted-password"))
                .isInstanceOfSatisfying(BusinessException.class,
                        exception -> assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.LOGIN_FAILED));

        verify(passwordEncoder).matches("submitted-password", "{bcrypt}dummy-hash");
        verify(refreshTokenRepository, never()).saveCurrent(anyLong(), any(), any(), any());
    }

    /**
     * 성공 로그인에서 원문이 아닌 해시와 30일 고정 만료 상태를 저장하는지 확인합니다.
     */
    @Test
    void successfulLoginStoresOnlyRefreshHashWithFixedExpiry() {
        UserAccount user = mock(UserAccount.class);
        LocalCredential credential = mock(LocalCredential.class);
        when(user.getId()).thenReturn(7L);
        when(credential.getPasswordHash()).thenReturn("{bcrypt}stored-hash");
        when(emailCanonicalizer.normalize("User@Example.COM"))
                .thenReturn(new NormalizedEmailAddress("User@Example.COM", "user@example.com"));
        when(userRepository.findByCanonicalEmail("user@example.com")).thenReturn(Optional.of(user));
        when(credentialRepository.findById(7L)).thenReturn(Optional.of(credential));
        when(passwordEncoder.matches("correct-password", "{bcrypt}stored-hash")).thenReturn(true);
        when(refreshTokenCodec.generate()).thenReturn(NEW_REFRESH);
        when(refreshTokenCodec.hashIfValid(NEW_REFRESH)).thenReturn(Optional.of(NEW_HASH));
        when(clock.instant()).thenReturn(NOW);
        when(accessTokenIssuer.issue(7L, NOW)).thenReturn("access-token");

        IssuedAuthentication issued = loginService.login("User@Example.COM", "correct-password");

        LocalDateTime now = LocalDateTime.ofInstant(NOW, ZoneOffset.UTC);
        verify(refreshTokenRepository).saveCurrent(7L, NEW_HASH, now, now.plusDays(30));
        verify(clock).instant();
        assertThat(issued.accessToken()).isEqualTo("access-token");
        assertThat(issued.refreshToken()).isEqualTo(NEW_REFRESH);
        assertThat(issued.refreshMaxAgeSeconds()).isEqualTo(30L * 24 * 60 * 60);
    }

    /**
     * 회전이 기존 만료를 연장하지 않고 1초 잔여 경계를 조건부 갱신에 전달하는지 확인합니다.
     */
    @Test
    void refreshPreservesFixedExpiryAndUsesConditionalRotation() {
        RefreshToken candidate = mock(RefreshToken.class);
        LocalDateTime now = LocalDateTime.ofInstant(NOW, ZoneOffset.UTC);
        when(refreshTokenCodec.hashIfValid(OLD_REFRESH)).thenReturn(Optional.of(OLD_HASH));
        when(refreshTokenRepository.findByTokenHash(OLD_HASH)).thenReturn(Optional.of(candidate));
        when(candidate.getUserId()).thenReturn(7L);
        when(candidate.getExpiresAt()).thenReturn(now.plusSeconds(1));
        when(refreshTokenCodec.generate()).thenReturn(NEW_REFRESH);
        when(refreshTokenCodec.hashIfValid(NEW_REFRESH)).thenReturn(Optional.of(NEW_HASH));
        when(clock.instant()).thenReturn(NOW);
        when(accessTokenIssuer.issue(7L, NOW)).thenReturn("rotated-access-token");
        when(refreshTokenRepository.rotate(7L, OLD_HASH, NEW_HASH, now, now.plusSeconds(1))).thenReturn(1);

        IssuedAuthentication issued = loginService.refresh(OLD_REFRESH);

        assertThat(issued.refreshMaxAgeSeconds()).isEqualTo(1);
        verify(clock).instant();
        verify(refreshTokenRepository).rotate(7L, OLD_HASH, NEW_HASH, now, now.plusSeconds(1));
    }

    /**
     * 현재 시각과 만료 시각이 같으면 회전과 새 토큰 발급 없이 거부하는지 확인합니다.
     */
    @Test
    void refreshRejectsExactExpiryBoundary() {
        RefreshToken candidate = mock(RefreshToken.class);
        LocalDateTime now = LocalDateTime.ofInstant(NOW, ZoneOffset.UTC);
        when(refreshTokenCodec.hashIfValid(OLD_REFRESH)).thenReturn(Optional.of(OLD_HASH));
        when(refreshTokenRepository.findByTokenHash(OLD_HASH)).thenReturn(Optional.of(candidate));
        when(candidate.getExpiresAt()).thenReturn(now);
        when(clock.instant()).thenReturn(NOW);

        assertThatThrownBy(() -> loginService.refresh(OLD_REFRESH))
                .isInstanceOfSatisfying(BusinessException.class,
                        exception -> assertThat(exception.getErrorCode())
                                .isEqualTo(ErrorCode.REFRESH_TOKEN_INVALID));

        verify(clock).instant();
        verify(accessTokenIssuer, never()).issue(anyLong(), any());
        verify(refreshTokenCodec, never()).generate();
    }

    /**
     * 동시 요청에 이미 교체된 이전 토큰은 새 발급값을 반환하지 않고 단일 실패로 끝나는지 확인합니다.
     */
    @Test
    void conditionalRotationLoserIsRejected() {
        RefreshToken candidate = mock(RefreshToken.class);
        LocalDateTime now = LocalDateTime.ofInstant(NOW, ZoneOffset.UTC);
        when(refreshTokenCodec.hashIfValid(OLD_REFRESH)).thenReturn(Optional.of(OLD_HASH));
        when(refreshTokenRepository.findByTokenHash(OLD_HASH)).thenReturn(Optional.of(candidate));
        when(candidate.getUserId()).thenReturn(7L);
        when(candidate.getExpiresAt()).thenReturn(now.plusMinutes(5));
        when(refreshTokenCodec.generate()).thenReturn(NEW_REFRESH);
        when(refreshTokenCodec.hashIfValid(NEW_REFRESH)).thenReturn(Optional.of(NEW_HASH));
        when(clock.instant()).thenReturn(NOW);
        when(accessTokenIssuer.issue(7L, NOW)).thenReturn("discarded-access-token");
        when(refreshTokenRepository.rotate(eq(7L), eq(OLD_HASH), eq(NEW_HASH), eq(now), eq(now.plusSeconds(1))))
                .thenReturn(0);

        assertThatThrownBy(() -> loginService.refresh(OLD_REFRESH))
                .isInstanceOfSatisfying(BusinessException.class,
                        exception -> assertThat(exception.getErrorCode())
                                .isEqualTo(ErrorCode.REFRESH_TOKEN_INVALID));
    }

    /**
     * 로그아웃은 올바른 형식의 쿠키만 해시 삭제하고 누락·형식 오류는 안전하게 무시하는지 확인합니다.
     */
    @Test
    void logoutDeletesOnlyValidlyFormattedRefreshToken() {
        when(refreshTokenCodec.hashIfValid(null)).thenReturn(Optional.empty());
        when(refreshTokenCodec.hashIfValid(OLD_REFRESH)).thenReturn(Optional.of(OLD_HASH));
        when(refreshTokenCodec.hashIfValid("malformed")).thenReturn(Optional.empty());

        loginService.logout(null);
        loginService.logout("malformed");
        loginService.logout(OLD_REFRESH);

        verify(refreshTokenRepository).deleteByTokenHashValue(OLD_HASH);
    }

    /**
     * 지정한 바이트로 채운 32바이트 해시를 생성합니다.
     *
     * @param value 각 바이트에 넣을 값
     * @return 독립된 32바이트 배열
     */
    private static byte[] filledHash(byte value) {
        byte[] hash = new byte[32];
        java.util.Arrays.fill(hash, value);
        return hash;
    }
}

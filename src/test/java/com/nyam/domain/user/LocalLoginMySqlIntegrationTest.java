package com.nyam.domain.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpHeaders;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import com.nyam.domain.user.model.UserAccount;
import com.nyam.domain.user.repository.UserAccountRepository;
import com.nyam.domain.user.service.IssuedAuthentication;
import com.nyam.domain.user.service.LocalLoginService;
import com.nyam.domain.user.service.RefreshTokenCodec;
import com.nyam.domain.user.web.LocalLoginController;
import com.nyam.global.exception.BusinessException;
import com.nyam.global.exception.ErrorCode;

import jakarta.servlet.http.Cookie;

/**
 * 실제 MySQL 8.4.5에서 로컬 로그인 세션 스키마, 고정 만료 회전, 폐기와 단일 승자를 검증합니다.
 */
@SpringBootTest(properties = {
        "NYAM_EMAIL_VERIFICATION_HMAC_SECRET=QUFBQUFBQUFBQUFBQUFBQUFBQUFBQUFBQUFBQUFBQUE=",
        "NYAM_AUTH_ACCESS_SECRET=QUFBQUFBQUFBQUFBQUFBQUFBQUFBQUFBQUFBQUFBQUE=",
        "spring.jpa.hibernate.ddl-auto=validate",
        "springdoc.api-docs.enabled=false",
        "springdoc.swagger-ui.enabled=false"
})
@AutoConfigureMockMvc
@Testcontainers(disabledWithoutDocker = true)
class LocalLoginMySqlIntegrationTest {

    private static final DockerImageName MYSQL_IMAGE = DockerImageName.parse("mysql:8.4.5");
    private static final Instant NOW = Instant.parse("2026-08-25T00:00:00Z");
    private static final String EMAIL = "User@Example.COM";
    private static final String CANONICAL_EMAIL = "user@example.com";
    private static final String PASSWORD = "safe-password-123";

    @Container
    @ServiceConnection
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>(MYSQL_IMAGE);

    @Autowired
    MockMvc mockMvc;
    @Autowired
    JdbcTemplate jdbcTemplate;
    @Autowired
    UserAccountRepository userRepository;
    @Autowired
    PasswordEncoder passwordEncoder;
    @Autowired
    LocalLoginService loginService;
    @Autowired
    RefreshTokenCodec refreshTokenCodec;
    @Autowired
    PlatformTransactionManager transactionManager;

    private ExecutorService executor;

    /**
     * 테스트별 사용자·토큰 데이터를 지우고 동시성 작업용 실행기를 준비합니다.
     */
    @BeforeEach
    void setUp() {
        jdbcTemplate.update("DELETE FROM refresh_tokens");
        jdbcTemplate.update("DELETE FROM user_consents");
        jdbcTemplate.update("DELETE FROM local_credentials");
        jdbcTemplate.update("DELETE FROM users");
        executor = Executors.newFixedThreadPool(2);
    }

    /**
     * 동시성 작업 실행기를 종료합니다.
     */
    @AfterEach
    void tearDown() {
        executor.shutdownNow();
    }

    /**
     * 로그인부터 회전, 이전 토큰 거절, 로그아웃과 사후 재발급 거절까지 실제 HTTP·DB 흐름을 확인합니다.
     *
     * @throws Exception MockMvc 요청이나 데이터베이스 검증에 실패한 경우
     */
    @Test
    void completeLoginRefreshLogoutFlowPreservesFixedExpiry() throws Exception {
        createLocalUser(EMAIL, CANONICAL_EMAIL);

        MvcResult firstLogin = performLogin();
        assertThat(firstLogin.getResponse().getStatus()).isEqualTo(200);
        String replacedByLogin = refreshValue(firstLogin);

        MvcResult login = performLogin();
        assertThat(login.getResponse().getStatus()).isEqualTo(200);
        String originalRefresh = refreshValue(login);
        assertThat(originalRefresh).isNotEqualTo(replacedByLogin);
        assertThat(countRefreshTokens()).isEqualTo(1);
        MvcResult staleLoginSession = performRefresh(replacedByLogin);
        assertThat(staleLoginSession.getResponse().getStatus()).isEqualTo(401);
        assertThat(staleLoginSession.getResponse().getHeader(HttpHeaders.SET_COOKIE)).isNull();
        assertThat(login.getResponse().getContentAsString())
                .contains("LOGIN_COMPLETED", "accessToken", "Bearer", "900")
                .doesNotContain(originalRefresh, "refreshToken");

        byte[] storedHash = jdbcTemplate.queryForObject(
                "SELECT token_hash FROM refresh_tokens", byte[].class);
        LocalDateTime originalExpiry = jdbcTemplate.queryForObject(
                "SELECT expires_at FROM refresh_tokens", LocalDateTime.class);
        assertThat(storedHash).containsExactly(refreshTokenCodec.hashIfValid(originalRefresh).orElseThrow());
        assertThat(originalExpiry).isEqualTo(LocalDateTime.ofInstant(NOW, ZoneOffset.UTC).plusDays(30));

        MvcResult refresh = performRefresh(originalRefresh);
        assertThat(refresh.getResponse().getStatus()).isEqualTo(200);
        String rotatedRefresh = refreshValue(refresh);
        assertThat(rotatedRefresh).isNotEqualTo(originalRefresh);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT expires_at FROM refresh_tokens", LocalDateTime.class)).isEqualTo(originalExpiry);

        MvcResult replaced = performRefresh(originalRefresh);
        assertThat(replaced.getResponse().getStatus()).isEqualTo(401);
        assertThat(replaced.getResponse().getHeader(HttpHeaders.SET_COOKIE)).isNull();
        assertThat(replaced.getResponse().getContentAsString()).contains("REFRESH_TOKEN_INVALID");

        MvcResult logout = performLogout(rotatedRefresh);
        assertThat(logout.getResponse().getStatus()).isEqualTo(200);
        assertThat(logout.getResponse().getHeader(HttpHeaders.SET_COOKIE))
                .contains("Path=/api/v1/auth", "Max-Age=0", "Secure", "HttpOnly", "SameSite=Strict");
        assertThat(countRefreshTokens()).isZero();

        assertThat(performLogout(rotatedRefresh).getResponse().getStatus()).isEqualTo(200);
        MvcResult afterLogout = performRefresh(rotatedRefresh);
        assertThat(afterLogout.getResponse().getStatus()).isEqualTo(401);
        assertThat(afterLogout.getResponse().getHeader(HttpHeaders.SET_COOKIE)).isNull();
    }

    /**
     * V3의 사용자당 한 행, 해시 유일성, 시간 순서와 사용자 삭제 연쇄 제약을 확인합니다.
     */
    @Test
    void v3ConstraintsEnforceTheSingleCurrentSessionModel() {
        long firstUserId = createLocalUser(EMAIL, CANONICAL_EMAIL);
        long secondUserId = createLocalUser("second@example.com", "second@example.com");
        byte[] hash = filledHash((byte) 3);
        LocalDateTime now = LocalDateTime.ofInstant(NOW, ZoneOffset.UTC);
        insertRefresh(firstUserId, hash, now, now.plusDays(30));

        assertThatThrownBy(() -> insertRefresh(secondUserId, hash, now, now.plusDays(30)))
                .isInstanceOf(DataAccessException.class);
        assertThatThrownBy(() -> insertRefresh(firstUserId, filledHash((byte) 4), now, now.plusDays(30)))
                .isInstanceOf(DataAccessException.class);
        assertThatThrownBy(() -> insertRefresh(secondUserId, filledHash((byte) 5), now, now))
                .isInstanceOf(DataAccessException.class);

        jdbcTemplate.update("DELETE FROM users WHERE user_id = ?", firstUserId);
        assertThat(countRefreshTokens()).isZero();
    }

    /**
     * 두 별도 트랜잭션의 조회를 먼저 맞춘 뒤 조건부 갱신과 실제 HTTP 경쟁에서 1승 1패를 확인합니다.
     *
     * @throws Exception 동시 작업 또는 MockMvc 요청에 실패한 경우
     */
    @Test
    void concurrentRefreshHasExactlyOneWinnerAndLoserWithoutCookie() throws Exception {
        createLocalUser(EMAIL, CANONICAL_EMAIL);
        IssuedAuthentication issued = loginService.login(EMAIL, PASSWORD);
        byte[] oldHash = refreshTokenCodec.hashIfValid(issued.refreshToken()).orElseThrow();
        CountDownLatch bothLookedUp = new CountDownLatch(2);

        Future<RotationAttempt> first = executor.submit(() -> rotateAfterCoordinatedLookup(
                oldHash, filledHash((byte) 6), bothLookedUp));
        Future<RotationAttempt> second = executor.submit(() -> rotateAfterCoordinatedLookup(
                oldHash, filledHash((byte) 7), bothLookedUp));
        List<RotationAttempt> attempts = List.of(first.get(20, TimeUnit.SECONDS), second.get(20, TimeUnit.SECONDS));

        assertThat(attempts).extracting(RotationAttempt::updatedRows).containsExactlyInAnyOrder(0, 1);
        assertThat(attempts).extracting(RotationAttempt::connectionId).doesNotHaveDuplicates();
        RotationAttempt winner = attempts.stream()
                .filter(attempt -> attempt.updatedRows() == 1)
                .findFirst().orElseThrow();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT token_hash FROM refresh_tokens", byte[].class)).containsExactly(winner.newHash());
        assertThatThrownBy(() -> loginService.refresh(issued.refreshToken()))
                .isInstanceOfSatisfying(BusinessException.class,
                        exception -> assertThat(exception.getErrorCode())
                                .isEqualTo(ErrorCode.REFRESH_TOKEN_INVALID));

        IssuedAuthentication httpIssued = loginService.login(EMAIL, PASSWORD);
        CountDownLatch start = new CountDownLatch(1);
        Future<MvcResult> firstResponse = executor.submit(() -> {
            await(start);
            return performRefresh(httpIssued.refreshToken());
        });
        Future<MvcResult> secondResponse = executor.submit(() -> {
            await(start);
            return performRefresh(httpIssued.refreshToken());
        });
        start.countDown();
        List<MvcResult> responses = List.of(
                firstResponse.get(20, TimeUnit.SECONDS), secondResponse.get(20, TimeUnit.SECONDS));

        assertThat(responses).extracting(result -> result.getResponse().getStatus())
                .containsExactlyInAnyOrder(200, 401);
        MvcResult httpWinner = responseWithStatus(responses, 200);
        MvcResult httpLoser = responseWithStatus(responses, 401);
        assertThat(httpWinner.getResponse().getHeader(HttpHeaders.SET_COOKIE))
                .contains(LocalLoginController.REFRESH_COOKIE_NAME + "=");
        assertThat(httpLoser.getResponse().getHeader(HttpHeaders.SET_COOKIE)).isNull();
        assertThat(performRefresh(httpIssued.refreshToken()).getResponse().getStatus()).isEqualTo(401);
    }

    /**
     * 실제 로컬 사용자와 BCrypt 자격 증명을 생성합니다.
     *
     * @param displayEmail 보존할 표기 이메일
     * @param canonicalEmail 로그인 조회용 정규화 이메일
     * @return 생성된 사용자 식별자
     */
    private long createLocalUser(String displayEmail, String canonicalEmail) {
        LocalDateTime now = LocalDateTime.ofInstant(NOW, ZoneOffset.UTC);
        UserAccount user = userRepository.saveAndFlush(new UserAccount(
                displayEmail, canonicalEmail, LocalDate.of(2000, 1, 1), now));
        jdbcTemplate.update("""
                INSERT INTO local_credentials(user_id, password_hash, created_at)
                VALUES (?, ?, ?)
                """, user.getId(), passwordEncoder.encode(PASSWORD), now);
        return user.getId();
    }

    /**
     * 표준 이메일과 비밀번호로 로그인 API를 호출합니다.
     *
     * @return 로그인 MVC 결과
     * @throws Exception MockMvc 요청 처리에 실패한 경우
     */
    private MvcResult performLogin() throws Exception {
        return mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"User@Example.COM","password":"safe-password-123"}
                                """))
                .andReturn();
    }

    /**
     * CSRF 표지와 지정 Refresh Token 쿠키로 재발급 API를 호출합니다.
     *
     * @param refreshToken 요청 쿠키 원문
     * @return 재발급 MVC 결과
     * @throws Exception MockMvc 요청 처리에 실패한 경우
     */
    private MvcResult performRefresh(String refreshToken) throws Exception {
        return mockMvc.perform(post("/api/v1/auth/refresh")
                        .header(LocalLoginController.CSRF_HEADER_NAME, "1")
                        .cookie(new Cookie(LocalLoginController.REFRESH_COOKIE_NAME, refreshToken)))
                .andReturn();
    }

    /**
     * CSRF 표지와 지정 Refresh Token 쿠키로 로그아웃 API를 호출합니다.
     *
     * @param refreshToken 선택적으로 삭제할 쿠키 원문
     * @return 로그아웃 MVC 결과
     * @throws Exception MockMvc 요청 처리에 실패한 경우
     */
    private MvcResult performLogout(String refreshToken) throws Exception {
        return mockMvc.perform(post("/api/v1/auth/logout")
                        .header(LocalLoginController.CSRF_HEADER_NAME, "1")
                        .cookie(new Cookie(LocalLoginController.REFRESH_COOKIE_NAME, refreshToken)))
                .andReturn();
    }

    /**
     * 응답 Set-Cookie에서 Refresh Token 원문만 추출합니다.
     *
     * @param result 로그인 또는 재발급 MVC 결과
     * @return 첫 세미콜론 전 쿠키 값
     */
    private String refreshValue(MvcResult result) {
        String setCookie = result.getResponse().getHeader(HttpHeaders.SET_COOKIE);
        assertThat(setCookie).isNotNull();
        String prefix = LocalLoginController.REFRESH_COOKIE_NAME + "=";
        return setCookie.substring(prefix.length(), setCookie.indexOf(';'));
    }

    /**
     * 두 조회가 모두 끝난 뒤 별도 트랜잭션에서 조건부 회전을 실행합니다.
     *
     * @param oldHash 두 요청이 함께 제출한 이전 해시
     * @param newHash 현재 요청이 쓰려는 새 해시
     * @param bothLookedUp 두 비잠금 조회 완료 장벽
     * @return 연결 식별자와 영향 행 수를 포함한 결과
     */
    private RotationAttempt rotateAfterCoordinatedLookup(
            byte[] oldHash, byte[] newHash, CountDownLatch bothLookedUp) {
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);
        return transaction.execute(status -> {
            Long connectionId = jdbcTemplate.queryForObject("SELECT CONNECTION_ID()", Long.class);
            Long userId = jdbcTemplate.queryForObject(
                    "SELECT user_id FROM refresh_tokens WHERE token_hash = ?", Long.class, oldHash);
            bothLookedUp.countDown();
            await(bothLookedUp);
            LocalDateTime now = LocalDateTime.ofInstant(NOW, ZoneOffset.UTC);
            int updated = jdbcTemplate.update("""
                    UPDATE refresh_tokens
                    SET token_hash = ?, issued_at = ?
                    WHERE user_id = ? AND token_hash = ? AND expires_at >= ?
                    """, newHash, now, userId, oldHash, now.plusSeconds(1));
            return new RotationAttempt(connectionId == null ? -1 : connectionId, newHash, updated);
        });
    }

    /**
     * Refresh Token 행을 제약 검증용 값으로 직접 삽입합니다.
     *
     * @param userId 소유 사용자 식별자
     * @param hash 32바이트 토큰 해시
     * @param issuedAt 발급 시각
     * @param expiresAt 만료 시각
     */
    private void insertRefresh(long userId, byte[] hash, LocalDateTime issuedAt, LocalDateTime expiresAt) {
        jdbcTemplate.update("""
                INSERT INTO refresh_tokens(user_id, token_hash, issued_at, expires_at)
                VALUES (?, ?, ?, ?)
                """, userId, hash, issuedAt, expiresAt);
    }

    /**
     * 현재 Refresh Token 행 수를 반환합니다.
     *
     * @return 서버에 남은 세션 행 수
     */
    private long countRefreshTokens() {
        Long count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM refresh_tokens", Long.class);
        return count == null ? 0 : count;
    }

    /**
     * 지정한 HTTP 상태의 유일한 결과를 찾습니다.
     *
     * @param responses 두 동시 HTTP 응답
     * @param status 찾을 HTTP 상태
     * @return 상태가 일치하는 결과
     */
    private MvcResult responseWithStatus(List<MvcResult> responses, int status) {
        return responses.stream()
                .filter(result -> result.getResponse().getStatus() == status)
                .findFirst().orElseThrow();
    }

    /**
     * 최대 10초 동안 동시성 장벽을 기다립니다.
     *
     * @param latch 기다릴 장벽
     */
    private void await(CountDownLatch latch) {
        try {
            if (!latch.await(10, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Timed out while coordinating refresh requests");
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while coordinating refresh requests", exception);
        }
    }

    /**
     * 지정한 값으로 채운 32바이트 테스트 해시를 만듭니다.
     *
     * @param value 각 바이트에 넣을 값
     * @return 32바이트 배열
     */
    private byte[] filledHash(byte value) {
        byte[] hash = new byte[32];
        Arrays.fill(hash, value);
        return hash;
    }

    /**
     * 한 조건부 회전의 DB 연결, 새 해시와 영향 행 수입니다.
     *
     * @param connectionId MySQL 물리 연결 식별자
     * @param newHash 이 요청이 저장하려 한 새 해시
     * @param updatedRows 조건부 갱신 영향 행 수
     */
    private record RotationAttempt(long connectionId, byte[] newHash, int updatedRows) {
    }

    /**
     * 인증 전체 테스트를 같은 UTC 시각으로 고정합니다.
     */
    @TestConfiguration
    static class FixedClockConfiguration {

        /**
         * 운영 시스템 시계보다 우선하는 테스트 UTC 시계를 제공합니다.
         *
         * @return {@link #NOW}에 고정된 시계
         */
        @Bean
        @Primary
        Clock fixedAuthenticationClock() {
            return Clock.fixed(NOW, ZoneOffset.UTC);
        }
    }
}

package com.nyam.domain.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import com.nyam.domain.user.model.EmailVerificationChallenge;
import com.nyam.domain.user.repository.EmailVerificationChallengeRepository;
import com.nyam.domain.user.repository.EmailVerificationProofRepository;
import com.nyam.domain.user.repository.UserAccountRepository;
import com.nyam.domain.user.service.EmailVerificationCodeGenerator;
import com.nyam.domain.user.service.EmailVerificationConfirmationResult;
import com.nyam.domain.user.service.EmailVerificationService;
import com.nyam.domain.user.service.VerificationMailSender;
import com.nyam.domain.user.service.VerificationProofHasher;
import com.nyam.global.exception.BusinessException;
import com.nyam.global.exception.ErrorCode;

/**
 * 실제 MySQL 8.4.5에서 이메일 인증 Migration, 잠금, 롤백과 proof 전환을 검증합니다.
 */
@SpringBootTest(properties =
        "NYAM_EMAIL_VERIFICATION_HMAC_SECRET=QUFBQUFBQUFBQUFBQUFBQUFBQUFBQUFBQUFBQUFBQUE=")
@Testcontainers(disabledWithoutDocker = true)
class EmailVerificationMySqlIntegrationTest {

    private static final DockerImageName MYSQL_IMAGE = DockerImageName.parse("mysql:8.4.5");
    private static final Instant NOW = Instant.parse("2026-08-21T00:00:00Z");
    private static final String DISPLAY_EMAIL = "User+tag@Example.COM";
    private static final String CANONICAL_EMAIL = "user+tag@example.com";
    private static final String CODE = validCode("mysql-current-code");
    private static final String NEXT_CODE = validCode("mysql-next-code");
    private static final String WRONG_CODE = differentCode(CODE);

    @Container
    @ServiceConnection
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>(MYSQL_IMAGE);

    @Autowired
    EmailVerificationService emailVerificationService;

    @Autowired
    EmailVerificationChallengeRepository challengeRepository;

    @Autowired
    EmailVerificationProofRepository proofRepository;

    @Autowired
    UserAccountRepository userRepository;

    @Autowired
    VerificationProofHasher proofHasher;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @Autowired
    PlatformTransactionManager transactionManager;

    @MockitoBean
    VerificationMailSender mailSender;

    @MockitoBean
    EmailVerificationCodeGenerator codeGenerator;

    @MockitoBean
    Clock clock;

    /**
     * 각 테스트 전에 격리된 테이블을 비우고 발급 시각과 현재 코드를 고정합니다.
     */
    @BeforeEach
    void setUp() {
        proofRepository.deleteAllInBatch();
        challengeRepository.deleteAllInBatch();
        userRepository.deleteAllInBatch();
        reset(mailSender, codeGenerator, clock);
        when(clock.instant()).thenReturn(NOW);
        when(codeGenerator.generate()).thenReturn(CODE);
    }

    /**
     * V2의 카운터 범위와 두 시간 순서 CHECK 제약이 실제 MySQL에서 거절되는지 확인합니다.
     */
    @Test
    void migrationEnforcesCounterAndTimeChecks() {
        LocalDateTime now = LocalDateTime.ofInstant(NOW, ZoneOffset.UTC);

        assertInvalidChallenge("resend-limit@example.com", now, now, now.plusMinutes(5), 4, 0);
        assertInvalidChallenge("attempt-limit@example.com", now, now, now.plusMinutes(5), 0, 6);
        assertInvalidChallenge("issue-order@example.com", now, now.minusSeconds(1), now.plusMinutes(5), 0, 0);
        assertInvalidChallenge("expiry-order@example.com", now, now, now, 0, 0);
        assertThat(challengeRepository.count()).isZero();
    }

    /**
     * 한 트랜잭션의 쓰기 잠금이 끝날 때까지 같은 이메일의 다음 잠금 조회가 대기하는지 확인합니다.
     *
     * @throws Exception 동시 실행 제어 또는 Future 대기에 실패한 경우
     */
    @Test
    void existingChallengeWriteLockSerializesSameEmailUpdates() throws Exception {
        emailVerificationService.sendCode(DISPLAY_EMAIL);
        CountDownLatch firstLocked = new CountDownLatch(1);
        CountDownLatch releaseFirst = new CountDownLatch(1);
        CountDownLatch secondLocked = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<?> first = executor.submit(() -> new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
                challengeRepository.findByCanonicalEmailForUpdate(CANONICAL_EMAIL).orElseThrow();
                firstLocked.countDown();
                await(releaseFirst);
            }));
            assertThat(firstLocked.await(5, TimeUnit.SECONDS)).isTrue();

            Future<?> second = executor.submit(() -> new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
                challengeRepository.findByCanonicalEmailForUpdate(CANONICAL_EMAIL).orElseThrow();
                secondLocked.countDown();
            }));

            assertThat(secondLocked.await(500, TimeUnit.MILLISECONDS)).isFalse();
            releaseFirst.countDown();
            first.get(5, TimeUnit.SECONDS);
            second.get(5, TimeUnit.SECONDS);
            assertThat(secondLocked.getCount()).isZero();
        } finally {
            releaseFirst.countDown();
            executor.shutdownNow();
        }
    }

    /**
     * 두 최초 요청이 경쟁해도 현재 행과 메일 발송이 하나만 남는지 확인합니다.
     *
     * @throws Exception 동시 실행 제어 또는 Future 대기에 실패한 경우
     */
    @Test
    void concurrentFirstRequestsCreateOneRowAndOneMail() throws Exception {
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<String> first = executor.submit(() -> sendAfter(start));
            Future<String> second = executor.submit(() -> sendAfter(start));
            start.countDown();

            List<String> outcomes = List.of(
                    first.get(10, TimeUnit.SECONDS), second.get(10, TimeUnit.SECONDS));

            assertThat(outcomes).contains("SUCCESS");
            assertThat(outcomes.stream().filter("SUCCESS"::equals).count()).isEqualTo(1);
            assertThat(outcomes.stream().filter(outcome -> outcome.equals("SEND_LIMITED")
                    || outcome.equals("INTERNAL")).count()).isEqualTo(1);
            assertThat(challengeRepository.count()).isEqualTo(1);
            verify(mailSender, times(1)).send(DISPLAY_EMAIL, CODE);
        } finally {
            executor.shutdownNow();
        }
    }

    /**
     * 다섯 번째 불일치가 커밋되고 재전송을 막지만 정확한 만료 시각에는 새 세션으로 초기화되는지 확인합니다.
     */
    @Test
    void fifthMismatchCommitsTerminalStateUntilExactExpiry() {
        emailVerificationService.sendCode(DISPLAY_EMAIL);

        for (int attempt = 1; attempt <= 5; attempt++) {
            EmailVerificationConfirmationResult result =
                    emailVerificationService.confirmCode(DISPLAY_EMAIL, WRONG_CODE);
            assertThat(result.errorCode()).isEqualTo(attempt < 5
                    ? ErrorCode.EMAIL_VERIFICATION_INVALID
                    : ErrorCode.EMAIL_VERIFICATION_ATTEMPTS_EXCEEDED);
        }

        EmailVerificationChallenge terminal = challengeRepository.findById(CANONICAL_EMAIL).orElseThrow();
        assertThat(terminal.getFailedAttemptCount()).isEqualTo(5);
        assertThatThrownBy(() -> emailVerificationService.sendCode(DISPLAY_EMAIL))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode())
                .isEqualTo(ErrorCode.EMAIL_VERIFICATION_SEND_LIMITED);

        when(clock.instant()).thenReturn(NOW.plusSeconds(300));
        when(codeGenerator.generate()).thenReturn(NEXT_CODE);
        emailVerificationService.sendCode(DISPLAY_EMAIL);

        EmailVerificationChallenge restarted = challengeRepository.findById(CANONICAL_EMAIL).orElseThrow();
        assertThat(restarted.getResendCount()).isZero();
        assertThat(restarted.getFailedAttemptCount()).isZero();
        assertThat(restarted.getVerificationStartedAt())
                .isEqualTo(LocalDateTime.ofInstant(NOW.plusSeconds(300), ZoneOffset.UTC));
    }

    /**
     * Mailpit 전달 실패가 최초 challenge 삽입을 실제 MySQL에서 롤백하는지 확인합니다.
     */
    @Test
    void mailFailureRollsBackChallengeState() {
        doThrow(new BusinessException(ErrorCode.EMAIL_DELIVERY_UNAVAILABLE))
                .when(mailSender).send(DISPLAY_EMAIL, CODE);

        assertThatThrownBy(() -> emailVerificationService.sendCode(DISPLAY_EMAIL))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode())
                .isEqualTo(ErrorCode.EMAIL_DELIVERY_UNAVAILABLE);
        assertThat(challengeRepository.count()).isZero();
    }

    /**
     * 성공 시 challenge가 proof로 원자 전환되고 같은 코드 재사용과 이전 proof 사용이 차단되는지 확인합니다.
     */
    @Test
    void successPreventsCodeReplayAndReplacesExistingProof() {
        when(codeGenerator.generate()).thenReturn(CODE, NEXT_CODE);
        emailVerificationService.sendCode(DISPLAY_EMAIL);
        EmailVerificationConfirmationResult first =
                emailVerificationService.confirmCode(DISPLAY_EMAIL, CODE);

        assertThat(first.errorCode()).isNull();
        assertThat(challengeRepository.count()).isZero();
        assertThat(proofRepository.findById(proofHasher.hash(first.verificationProof()))).isPresent();
        assertThat(emailVerificationService.confirmCode(DISPLAY_EMAIL, CODE).errorCode())
                .isEqualTo(ErrorCode.EMAIL_VERIFICATION_INVALID);

        when(clock.instant()).thenReturn(NOW.plusSeconds(60));
        emailVerificationService.sendCode(DISPLAY_EMAIL);
        EmailVerificationConfirmationResult second =
                emailVerificationService.confirmCode(DISPLAY_EMAIL, NEXT_CODE);

        assertThat(second.errorCode()).isNull();
        assertThat(proofRepository.count()).isEqualTo(1);
        assertThat(proofRepository.findById(proofHasher.hash(first.verificationProof()))).isEmpty();
        assertThat(proofRepository.findById(proofHasher.hash(second.verificationProof()))).isPresent();
    }

    /**
     * 지정한 값으로 challenge 행을 직접 삽입해 CHECK 제약 위반을 확인합니다.
     *
     * @param canonicalEmail 행을 구분할 테스트 이메일
     * @param startedAt 세션 시작 시각
     * @param issuedAt 코드 발급 시각
     * @param expiresAt 코드 만료 시각
     * @param resendCount 재전송 횟수
     * @param failedAttemptCount 오입력 횟수
     */
    private void assertInvalidChallenge(String canonicalEmail, LocalDateTime startedAt,
            LocalDateTime issuedAt, LocalDateTime expiresAt, int resendCount, int failedAttemptCount) {
        assertThatThrownBy(() -> jdbcTemplate.update("""
                INSERT INTO email_verification_challenges (
                    canonical_email, display_email, code_verifier,
                    verification_started_at, code_issued_at, expires_at,
                    resend_count, failed_attempt_count
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                """, canonicalEmail, canonicalEmail, new byte[32], startedAt, issuedAt, expiresAt,
                resendCount, failedAttemptCount))
                .isInstanceOf(DataAccessException.class);
    }

    /**
     * 동시 시작 신호를 기다린 뒤 발송 결과를 데이터 무결성 관점의 문자열로 변환합니다.
     *
     * @param start 두 요청을 동시에 출발시킬 신호
     * @return 성공, 일반 중복 제한 또는 드문 내부 잠금 실패 결과
     */
    private String sendAfter(CountDownLatch start) {
        await(start);
        try {
            emailVerificationService.sendCode(DISPLAY_EMAIL);
            return "SUCCESS";
        } catch (BusinessException exception) {
            return exception.getErrorCode() == ErrorCode.EMAIL_VERIFICATION_SEND_LIMITED
                    ? "SEND_LIMITED" : "INTERNAL";
        } catch (RuntimeException exception) {
            return "INTERNAL";
        }
    }

    /**
     * 인터럽트 상태를 보존하면서 테스트 동시성 신호를 기다립니다.
     *
     * @param latch 기다릴 동시성 신호
     * @throws IllegalStateException 대기 중 인터럽트된 경우
     */
    private void await(CountDownLatch latch) {
        try {
            latch.await();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Concurrent verification test was interrupted", exception);
        }
    }

    /**
     * 고정된 설명 문자열에서 유효한 6자리 테스트 코드를 계산합니다.
     *
     * @param seed 코드 생성에 사용할 설명 문자열
     * @return 6자리 ASCII 숫자 문자열
     */
    private static String validCode(String seed) {
        return String.format("%06d", Math.floorMod(seed.hashCode(), 1_000_000));
    }

    /**
     * 기준 코드와 반드시 다른 유효한 6자리 문자열을 계산합니다.
     *
     * @param code 기준 인증번호
     * @return 기준과 다른 6자리 인증번호
     */
    private static String differentCode(String code) {
        return String.format("%06d", (Integer.parseInt(code) + 1) % 1_000_000);
    }
}

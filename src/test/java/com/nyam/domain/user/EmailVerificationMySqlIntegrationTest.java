package com.nyam.domain.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import com.nyam.domain.user.model.EmailVerificationChallenge;
import com.nyam.domain.user.repository.EmailVerificationChallengeRepository;
import com.nyam.domain.user.repository.UserAccountRepository;
import com.nyam.domain.user.service.EmailVerificationCodeGenerator;
import com.nyam.domain.user.service.EmailVerificationCodeVerifier;
import com.nyam.domain.user.service.EmailVerificationService;
import com.nyam.domain.user.service.RegisterUserCommand;
import com.nyam.domain.user.service.RegisterUserResult;
import com.nyam.domain.user.service.UserRegistrationService;
import com.nyam.domain.user.service.VerificationMailSender;
import com.nyam.global.exception.BusinessException;
import com.nyam.global.exception.ErrorCode;

/**
 * 실제 MySQL 8.4.5에서 발송 원자성, 최초 발송 경쟁과 발송·가입 경쟁을 검증합니다.
 */
@SpringBootTest(properties = {
        "NYAM_EMAIL_VERIFICATION_HMAC_SECRET=QUFBQUFBQUFBQUFBQUFBQUFBQUFBQUFBQUFBQUFBQUE=",
        "NYAM_AUTH_ACCESS_SECRET=QUFBQUFBQUFBQUFBQUFBQUFBQUFBQUFBQUFBQUFBQUE="
})
@Testcontainers(disabledWithoutDocker = true)
class EmailVerificationMySqlIntegrationTest {

    private static final DockerImageName MYSQL_IMAGE = DockerImageName.parse("mysql:8.4.5");
    private static final Instant NOW = Instant.parse("2026-08-27T00:00:00Z");
    private static final String DISPLAY_EMAIL = "User+tag@Example.COM";
    private static final String CANONICAL_EMAIL = "user+tag@example.com";
    private static final String CODE = "012345";
    private static final String NEXT_CODE = "543210";

    @Container
    @ServiceConnection
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>(MYSQL_IMAGE);

    @Autowired
    EmailVerificationService emailVerificationService;

    @Autowired
    UserRegistrationService registrationService;

    @Autowired
    EmailVerificationChallengeRepository challengeRepository;

    @Autowired
    EmailVerificationCodeVerifier codeVerifier;

    @Autowired
    UserAccountRepository userRepository;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @MockitoBean
    VerificationMailSender mailSender;

    @MockitoBean
    EmailVerificationCodeGenerator codeGenerator;

    @MockitoBean
    Clock clock;

    @BeforeEach
    void setUp() {
        jdbcTemplate.update("DELETE FROM refresh_tokens");
        jdbcTemplate.update("DELETE FROM user_consents");
        jdbcTemplate.update("DELETE FROM local_credentials");
        userRepository.deleteAllInBatch();
        challengeRepository.deleteAllInBatch();
        reset(mailSender, codeGenerator, clock);
        when(clock.instant()).thenReturn(NOW);
        when(clock.getZone()).thenReturn(ZoneOffset.UTC);
        when(codeGenerator.generate()).thenReturn(CODE);
    }

    @Test
    void forwardMigrationRemovedProofTableAndPreservedChallengeTable() {
        assertThat(tableExists("email_verification_proofs")).isFalse();
        assertThat(tableExists("email_verification_challenges")).isTrue();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM flyway_schema_history WHERE version = '4' AND success = TRUE",
                Integer.class)).isEqualTo(1);
    }

    @Test
    void mailFailureRollsBackInitialChallenge() {
        doThrow(new BusinessException(ErrorCode.EMAIL_DELIVERY_UNAVAILABLE))
                .when(mailSender).send(DISPLAY_EMAIL, CODE);

        assertThatThrownBy(() -> emailVerificationService.sendCode(DISPLAY_EMAIL))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode())
                .isEqualTo(ErrorCode.EMAIL_DELIVERY_UNAVAILABLE);
        assertThat(challengeRepository.count()).isZero();
    }

    @Test
    void concurrentFirstSendHasOneMailAndOneLimitedLoser() throws Exception {
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<String> first = executor.submit(() -> sendAfter(start));
            Future<String> second = executor.submit(() -> sendAfter(start));
            start.countDown();

            assertThat(List.of(first.get(10, TimeUnit.SECONDS), second.get(10, TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder("SUCCESS", "SEND_LIMITED");
            assertThat(challengeRepository.count()).isEqualTo(1);
            verify(mailSender, times(1)).send(DISPLAY_EMAIL, CODE);
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void resendWinningRaceMakesOldSignupCodeInvalid() throws Exception {
        LocalDateTime initial = LocalDateTime.ofInstant(NOW, ZoneOffset.UTC);
        challengeRepository.saveAndFlush(new EmailVerificationChallenge(
                CANONICAL_EMAIL,
                DISPLAY_EMAIL,
                codeVerifier.hash(CANONICAL_EMAIL, CODE),
                initial.minusSeconds(60),
                initial.plusSeconds(240)));
        when(clock.instant()).thenReturn(NOW.plusSeconds(60));
        when(codeGenerator.generate()).thenReturn(NEXT_CODE);

        CountDownLatch mailEntered = new CountDownLatch(1);
        CountDownLatch releaseMail = new CountDownLatch(1);
        doAnswer(invocation -> {
            mailEntered.countDown();
            releaseMail.await(5, TimeUnit.SECONDS);
            return null;
        }).when(mailSender).send(DISPLAY_EMAIL, NEXT_CODE);

        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<?> resend = executor.submit(() -> emailVerificationService.sendCode(DISPLAY_EMAIL));
            assertThat(mailEntered.await(5, TimeUnit.SECONDS)).isTrue();
            Future<RegisterUserResult> signup = executor.submit(() -> registrationService.register(
                    new RegisterUserCommand(
                            DISPLAY_EMAIL,
                            CODE,
                            "safe-password",
                            LocalDate.of(2000, 1, 1),
                            true,
                            true,
                            true)));
            releaseMail.countDown();

            resend.get(10, TimeUnit.SECONDS);
            assertThat(signup.get(10, TimeUnit.SECONDS).errorCode())
                    .isEqualTo(ErrorCode.EMAIL_VERIFICATION_INVALID);
            assertThat(userRepository.count()).isZero();
            assertThat(challengeRepository.findById(CANONICAL_EMAIL).orElseThrow()
                    .getFailedAttemptCount()).isEqualTo(1);
        } finally {
            releaseMail.countDown();
            executor.shutdownNow();
        }
    }

    private String sendAfter(CountDownLatch start) {
        try {
            start.await(5, TimeUnit.SECONDS);
            emailVerificationService.sendCode(DISPLAY_EMAIL);
            return "SUCCESS";
        } catch (BusinessException exception) {
            return exception.getErrorCode() == ErrorCode.EMAIL_VERIFICATION_SEND_LIMITED
                    ? "SEND_LIMITED"
                    : exception.getErrorCode().name();
        } catch (Exception exception) {
            return exception.getClass().getSimpleName();
        }
    }

    private boolean tableExists(String tableName) {
        Integer count = jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM information_schema.tables
                WHERE table_schema = DATABASE() AND table_name = ?
                """, Integer.class, tableName);
        return count != null && count == 1;
    }
}

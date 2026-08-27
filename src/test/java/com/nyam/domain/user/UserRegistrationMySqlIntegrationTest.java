package com.nyam.domain.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.reset;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

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
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import com.nyam.domain.user.model.ConsentType;
import com.nyam.domain.user.model.EmailVerificationChallenge;
import com.nyam.domain.user.policy.ConsentAgreement;
import com.nyam.domain.user.policy.ConsentPolicy;
import com.nyam.domain.user.repository.EmailVerificationChallengeRepository;
import com.nyam.domain.user.service.EmailVerificationCodeVerifier;
import com.nyam.domain.user.service.RegisterUserCommand;
import com.nyam.domain.user.service.UserRegistrationService;

/**
 * 실제 MySQL 8.4.5에서 직접 인증번호 회원가입의 원자성과 동시성을 검증합니다.
 */
@SpringBootTest(properties = {
        "NYAM_EMAIL_VERIFICATION_HMAC_SECRET=QUFBQUFBQUFBQUFBQUFBQUFBQUFBQUFBQUFBQUFBQUE=",
        "NYAM_AUTH_ACCESS_SECRET=QUFBQUFBQUFBQUFBQUFBQUFBQUFBQUFBQUFBQUFBQUE="
})
@AutoConfigureMockMvc
@Testcontainers(disabledWithoutDocker = true)
class UserRegistrationMySqlIntegrationTest {

    private static final DockerImageName MYSQL_IMAGE = DockerImageName.parse("mysql:8.4.5");
    private static final Instant NOW = Instant.parse("2026-08-27T00:00:00Z");
    private static final String DISPLAY_EMAIL = "User@Example.COM";
    private static final String CANONICAL_EMAIL = "user@example.com";
    private static final String CODE = "012345";
    private static final String PASSWORD = "safe-password";

    @Container
    @ServiceConnection
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>(MYSQL_IMAGE);

    @Autowired
    MockMvc mockMvc;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @Autowired
    EmailVerificationChallengeRepository challengeRepository;

    @Autowired
    EmailVerificationCodeVerifier codeVerifier;

    @Autowired
    UserRegistrationService registrationService;

    @Autowired
    PasswordEncoder passwordEncoder;

    @MockitoSpyBean
    ConsentPolicy consentPolicy;

    @BeforeEach
    void cleanDatabase() {
        reset(consentPolicy);
        jdbcTemplate.update("DELETE FROM refresh_tokens");
        jdbcTemplate.update("DELETE FROM user_consents");
        jdbcTemplate.update("DELETE FROM local_credentials");
        jdbcTemplate.update("DELETE FROM users");
        jdbcTemplate.update("DELETE FROM email_verification_challenges");
    }

    @Test
    void successfulSignupUsesChallengeDisplayAndServerConsentVersion() throws Exception {
        insertChallenge(DISPLAY_EMAIL, CANONICAL_EMAIL, CODE);

        var response = mockMvc.perform(post("/api/v1/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(signupJson("user@example.com", CODE, true)))
                .andReturn().getResponse();

        assertThat(response.getStatus()).isEqualTo(201);
        assertThat(response.getContentAsString())
                .contains("SIGNUP_COMPLETED", DISPLAY_EMAIL)
                .doesNotContain(CODE, PASSWORD, "canonicalEmail");
        assertThat(count("users")).isEqualTo(1);
        assertThat(count("local_credentials")).isEqualTo(1);
        assertThat(count("user_consents")).isEqualTo(3);
        assertThat(count("email_verification_challenges")).isZero();
        assertThat(jdbcTemplate.queryForList(
                "SELECT DISTINCT consent_version FROM user_consents", String.class))
                .containsExactly("1.0");

        String encoded = jdbcTemplate.queryForObject(
                "SELECT password_hash FROM local_credentials", String.class);
        assertThat(encoded).startsWith("{bcrypt}").isNotEqualTo(PASSWORD);
        assertThat(passwordEncoder.matches(PASSWORD, encoded)).isTrue();
    }

    @Test
    void wrongCodeCountCommitsAndFifthAttemptIsLimited() throws Exception {
        insertChallenge(DISPLAY_EMAIL, CANONICAL_EMAIL, CODE);

        for (int attempt = 1; attempt <= 5; attempt++) {
            int status = mockMvc.perform(post("/api/v1/auth/signup")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(signupJson(DISPLAY_EMAIL, "999999", true)))
                    .andReturn().getResponse().getStatus();
            assertThat(status).isEqualTo(attempt < 5 ? 422 : 429);
        }

        assertThat(jdbcTemplate.queryForObject(
                "SELECT failed_attempt_count FROM email_verification_challenges", Integer.class))
                .isEqualTo(5);
        assertThat(count("users")).isZero();
    }

    @Test
    void invalidConsentDoesNotConsumeAttempt() throws Exception {
        insertChallenge(DISPLAY_EMAIL, CANONICAL_EMAIL, CODE);

        assertThat(mockMvc.perform(post("/api/v1/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(signupJson(DISPLAY_EMAIL, "999999", false)))
                .andReturn().getResponse().getStatus()).isEqualTo(422);

        assertThat(jdbcTemplate.queryForObject(
                "SELECT failed_attempt_count FROM email_verification_challenges", Integer.class))
                .isZero();
    }

    @Test
    void accountPersistenceFailureRollsBackChallengeConsumption() {
        insertChallenge(DISPLAY_EMAIL, CANONICAL_EMAIL, CODE);
        List<ConsentAgreement> duplicate = List.of(
                new ConsentAgreement(ConsentType.TERMS, "1.0"),
                new ConsentAgreement(ConsentType.TERMS, "1.0"),
                new ConsentAgreement(ConsentType.HEALTH_INFORMATION, "1.0"));
        doReturn(duplicate).when(consentPolicy).resolveRequired(any(), any(), any());

        assertThatThrownBy(() -> registrationService.register(command(DISPLAY_EMAIL, CODE)))
                .isInstanceOf(RuntimeException.class);

        assertThat(count("users")).isZero();
        assertThat(count("local_credentials")).isZero();
        assertThat(count("user_consents")).isZero();
        assertThat(count("email_verification_challenges")).isEqualTo(1);
    }

    @Test
    void concurrentSameCodeSignupHasSingleWinner() throws Exception {
        insertChallenge(DISPLAY_EMAIL, CANONICAL_EMAIL, CODE);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<Integer> first = executor.submit(() -> signupAfter(start));
            Future<Integer> second = executor.submit(() -> signupAfter(start));
            start.countDown();

            assertThat(List.of(first.get(10, TimeUnit.SECONDS), second.get(10, TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder(201, 422);
            assertThat(count("users")).isEqualTo(1);
            assertThat(count("local_credentials")).isEqualTo(1);
            assertThat(count("user_consents")).isEqualTo(3);
            assertThat(count("email_verification_challenges")).isZero();
        } finally {
            executor.shutdownNow();
        }
    }

    private int signupAfter(CountDownLatch start) throws Exception {
        start.await(5, TimeUnit.SECONDS);
        return mockMvc.perform(post("/api/v1/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(signupJson(DISPLAY_EMAIL, CODE, true)))
                .andReturn().getResponse().getStatus();
    }

    private void insertChallenge(String displayEmail, String canonicalEmail, String code) {
        LocalDateTime now = LocalDateTime.ofInstant(NOW, ZoneOffset.UTC);
        challengeRepository.saveAndFlush(new EmailVerificationChallenge(
                canonicalEmail,
                displayEmail,
                codeVerifier.hash(canonicalEmail, code),
                now.minusSeconds(60),
                now.plusSeconds(240)));
    }

    private RegisterUserCommand command(String email, String code) {
        return new RegisterUserCommand(
                email, code, PASSWORD, LocalDate.of(2000, 1, 1), true, true, true);
    }

    private String signupJson(String email, String code, boolean personalInformationAgreed) {
        return """
                {
                  "email": "%s",
                  "verificationCode": "%s",
                  "password": "%s",
                  "birthDate": "2000-01-01",
                  "termsAgreed": true,
                  "personalInformationAgreed": %s,
                  "healthInformationAgreed": true
                }
                """.formatted(email, code, PASSWORD, personalInformationAgreed);
    }

    private long count(String table) {
        Long result = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM " + table, Long.class);
        return result == null ? 0 : result;
    }

    @TestConfiguration
    static class FixedClockConfiguration {

        @Bean
        @Primary
        Clock fixedRegistrationClock() {
            return Clock.fixed(NOW, ZoneOffset.UTC);
        }
    }
}

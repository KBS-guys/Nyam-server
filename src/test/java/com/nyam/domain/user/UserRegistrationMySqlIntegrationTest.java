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

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.dao.DataIntegrityViolationException;
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
import com.nyam.domain.user.model.EmailVerificationProof;
import com.nyam.domain.user.policy.ConsentAgreement;
import com.nyam.domain.user.policy.ConsentPolicy;
import com.nyam.domain.user.repository.EmailVerificationProofRepository;
import com.nyam.domain.user.service.RegisterUserCommand;
import com.nyam.domain.user.service.UserRegistrationService;
import com.nyam.domain.user.service.VerificationProofHasher;

/**
 * 실제 MySQL에서 회원가입 성공, proof 소비, 중복 방지와 트랜잭션 롤백을 검증합니다.
 */
@SpringBootTest(properties =
        "NYAM_EMAIL_VERIFICATION_HMAC_SECRET=QUFBQUFBQUFBQUFBQUFBQUFBQUFBQUFBQUFBQUFBQUE=")
@AutoConfigureMockMvc
@Testcontainers(disabledWithoutDocker = true)
class UserRegistrationMySqlIntegrationTest {

    private static final DockerImageName MYSQL_IMAGE = DockerImageName.parse("mysql:8.4.5");
    private static final Instant NOW = Instant.parse("2026-08-14T00:00:00Z");
    private static final String PASSWORD = "password";

    @Container
    @ServiceConnection
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>(MYSQL_IMAGE);

    @Autowired
    MockMvc mockMvc;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @Autowired
    VerificationProofHasher proofHasher;

    @Autowired
    EmailVerificationProofRepository proofRepository;

    @Autowired
    UserRegistrationService registrationService;

    @Autowired
    PasswordEncoder passwordEncoder;

    @MockitoSpyBean
    ConsentPolicy consentPolicy;

    /**
     * 테스트별 데이터와 정책 스파이 상태를 초기화합니다.
     */
    @BeforeEach
    void cleanDatabase() {
        reset(consentPolicy);
        jdbcTemplate.update("DELETE FROM user_consents");
        jdbcTemplate.update("DELETE FROM local_credentials");
        jdbcTemplate.update("DELETE FROM users");
        jdbcTemplate.update("DELETE FROM email_verification_proofs");
    }

    /**
     * 신규 스키마에서 회원가입 성공 응답과 네 테이블의 저장 결과를 검증합니다.
     *
     * @throws Exception MockMvc 요청 또는 데이터베이스 검증에 실패한 경우
     */
    @Test
    void freshSchemaAndSuccessfulSignupMatchTheApprovedContract() throws Exception {
        String proof = proof('A');
        insertValidProof(proof, "User@Example.COM", "user@example.com");

        var response = mockMvc.perform(post("/api/v1/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(signupJson(proof, PASSWORD)))
                .andReturn().getResponse();

        assertThat(response.getStatus()).isEqualTo(201);
        assertThat(response.getContentAsString()).contains("SIGNUP_COMPLETED", "User@Example.COM")
                .doesNotContain("userId", "token", proof, PASSWORD, "canonicalEmail");
        assertThat(count("users")).isEqualTo(1);
        assertThat(count("local_credentials")).isEqualTo(1);
        assertThat(count("user_consents")).isEqualTo(3);
        assertThat(count("email_verification_proofs")).isZero();

        String encoded = jdbcTemplate.queryForObject(
                "SELECT password_hash FROM local_credentials", String.class);
        assertThat(encoded).startsWith("{bcrypt}").isNotEqualTo(PASSWORD);
        assertThat(passwordEncoder.matches(PASSWORD, encoded)).isTrue();
    }

    /**
     * 성공적으로 소비된 인증 증명을 다시 사용할 수 없는지 검증합니다.
     *
     * @throws Exception MockMvc 요청 처리에 실패한 경우
     */
    @Test
    void consumedProofCannotBeReplayed() throws Exception {
        String proof = proof('B');
        insertValidProof(proof, "replay@example.com", "replay@example.com");

        assertThat(performSignup(proof)).isEqualTo(201);
        assertThat(performSignup(proof)).isEqualTo(422);
        assertThat(count("users")).isEqualTo(1);
    }

    /**
     * 만료된 proof와 이미 가입된 canonical email이 추가 계정 생성을 막는지 검증합니다.
     *
     * @throws Exception MockMvc 요청 처리에 실패한 경우
     */
    @Test
    void expiredProofAndDuplicateEmailCreateNoAdditionalAccount() throws Exception {
        String expiredProof = proof('C');
        LocalDateTime now = LocalDateTime.ofInstant(NOW, ZoneOffset.UTC);
        proofRepository.saveAndFlush(new EmailVerificationProof(
                proofHasher.hash(expiredProof),
                "expired@example.com",
                "expired@example.com",
                now.minusMinutes(16),
                now));
        assertThat(performSignup(expiredProof)).isEqualTo(422);

        String firstProof = proof('D');
        insertValidProof(firstProof, "duplicate@example.com", "duplicate@example.com");
        assertThat(performSignup(firstProof)).isEqualTo(201);

        String secondProof = proof('E');
        insertValidProof(secondProof, "Duplicate@Example.COM", "duplicate@example.com");
        assertThat(performSignup(secondProof)).isEqualTo(409);
        assertThat(count("users")).isEqualTo(1);
        assertThat(count("email_verification_proofs")).isEqualTo(2);
    }

    /**
     * 사용자 저장 이후 실제 데이터베이스 실패가 모든 계정 행과 proof 삭제를 롤백하는지 검증합니다.
     *
     * @throws Exception 롤백 후 재시도 요청 처리에 실패한 경우
     */
    @Test
    void databaseFailureRollsBackAccountAndKeepsProofForRetry() throws Exception {
        String proof = proof('F');
        insertValidProof(proof, "rollback@example.com", "rollback@example.com");
        List<ConsentAgreement> duplicateConsents = List.of(
                new ConsentAgreement(ConsentType.TERMS, "1.0"),
                new ConsentAgreement(ConsentType.TERMS, "1.0"),
                new ConsentAgreement(ConsentType.HEALTH_INFORMATION, "1.0"));
        doReturn(duplicateConsents).when(consentPolicy).validate(any());

        assertThatThrownBy(() -> registrationService.register(new RegisterUserCommand(
                proof,
                PASSWORD,
                LocalDate.of(2000, 1, 1),
                validConsents())))
                .isInstanceOf(DataIntegrityViolationException.class);

        assertThat(count("users")).isZero();
        assertThat(count("local_credentials")).isZero();
        assertThat(count("user_consents")).isZero();
        assertThat(count("email_verification_proofs")).isEqualTo(1);

        reset(consentPolicy);
        assertThat(performSignup(proof)).isEqualTo(201);
    }

    /**
     * canonical email UNIQUE 제약과 사용자 삭제 시 자격 증명·동의 연쇄 삭제를 검증합니다.
     *
     * @throws Exception 회원가입 MockMvc 요청 처리에 실패한 경우
     */
    @Test
    void canonicalEmailConstraintAndOwnedCascadesRemainEnforced() throws Exception {
        String proof = proof('G');
        insertValidProof(proof, "cascade@example.com", "cascade@example.com");
        assertThat(performSignup(proof)).isEqualTo(201);

        assertThatThrownBy(() -> jdbcTemplate.update("""
                INSERT INTO users(display_email, canonical_email, birth_date, created_at)
                VALUES ('CASE@example.com', 'cascade@example.com', '2000-01-01', UTC_TIMESTAMP(6))
                """)).isInstanceOf(Exception.class);

        jdbcTemplate.update("DELETE FROM users WHERE canonical_email = ?", "cascade@example.com");
        assertThat(count("local_credentials")).isZero();
        assertThat(count("user_consents")).isZero();
    }

    /**
     * 표준 유효 요청으로 회원가입 API를 호출합니다.
     *
     * @param proof 요청에 사용할 원문 이메일 인증 증명
     * @return 회원가입 HTTP 상태 코드
     * @throws Exception MockMvc 요청 처리에 실패한 경우
     */
    private int performSignup(String proof) throws Exception {
        return mockMvc.perform(post("/api/v1/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(signupJson(proof, PASSWORD)))
                .andReturn().getResponse().getStatus();
    }

    /**
     * 고정 시각 기준으로 유효한 이메일 인증 증명을 데이터베이스에 저장합니다.
     *
     * @param proof 해시할 원문 이메일 인증 증명
     * @param displayEmail 사용자에게 표시할 원본 표기 이메일
     * @param canonicalEmail 중복 비교용 canonical email
     */
    private void insertValidProof(String proof, String displayEmail, String canonicalEmail) {
        LocalDateTime now = LocalDateTime.ofInstant(NOW, ZoneOffset.UTC);
        proofRepository.saveAndFlush(new EmailVerificationProof(
                proofHasher.hash(proof), displayEmail, canonicalEmail,
                now.minusMinutes(1), now.plusMinutes(14)));
    }

    /**
     * 허용된 회원가입 테이블의 현재 행 수를 조회합니다.
     *
     * @param table 행 수를 확인할 테이블명
     * @return 해당 테이블의 행 수
     */
    private long count(String table) {
        Long result = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM " + table, Long.class);
        return result == null ? 0 : result;
    }

    /**
     * 공개 계약에 맞는 43자 테스트용 이메일 인증 증명을 생성합니다.
     *
     * @param character 증명을 채울 URL-safe ASCII 문자
     * @return 동일 문자를 43번 반복한 원문 증명
     */
    private String proof(char character) {
        return String.valueOf(character).repeat(43);
    }

    /**
     * 현재 버전의 필수 동의 세 종류를 생성합니다.
     *
     * @return 회원가입 요청과 정책 스파이에 사용할 동의 목록
     */
    private List<ConsentAgreement> validConsents() {
        return List.of(
                new ConsentAgreement(ConsentType.TERMS, "1.0"),
                new ConsentAgreement(ConsentType.PERSONAL_INFORMATION, "1.0"),
                new ConsentAgreement(ConsentType.HEALTH_INFORMATION, "1.0"));
    }

    /**
     * 필수 동의 세 종류를 포함한 회원가입 JSON 본문을 생성합니다.
     *
     * @param proof 요청에 포함할 원문 이메일 인증 증명
     * @param password 요청에 포함할 테스트 비밀번호
     * @return 회원가입 API에 전송할 JSON 문자열
     */
    private String signupJson(String proof, String password) {
        return """
                {
                  "verificationProof": "%s",
                  "password": "%s",
                  "birthDate": "2000-01-01",
                  "consents": [
                    {"type": "TERMS", "version": "1.0"},
                    {"type": "PERSONAL_INFORMATION", "version": "1.0"},
                    {"type": "HEALTH_INFORMATION", "version": "1.0"}
                  ]
                }
                """.formatted(proof, password);
    }

    /**
     * 통합 테스트의 만료와 연령 계산을 재현 가능하게 만드는 고정 시계 구성입니다.
     */
    @TestConfiguration
    static class FixedClockConfiguration {

        /**
         * 회원가입 구성의 시스템 시계보다 우선하는 테스트용 UTC 시계를 제공합니다.
         *
         * @return {@link #NOW}에 고정된 UTC 시계
         */
        @Bean
        @Primary
        Clock fixedRegistrationClock() {
            return Clock.fixed(NOW, ZoneOffset.UTC);
        }
    }
}

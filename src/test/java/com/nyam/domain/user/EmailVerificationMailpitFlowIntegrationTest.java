package com.nyam.domain.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.nyam.domain.user.repository.EmailVerificationChallengeRepository;
import com.nyam.domain.user.repository.EmailVerificationProofRepository;
import com.nyam.domain.user.repository.UserAccountRepository;

/**
 * 실제 MySQL·Mailpit과 HTTP API를 연결해 인증번호 발송부터 기존 signup까지 검증합니다.
 */
@SpringBootTest(properties = {
        "NYAM_EMAIL_VERIFICATION_HMAC_SECRET=QUFBQUFBQUFBQUFBQUFBQUFBQUFBQUFBQUFBQUFBQUE=",
        "springdoc.api-docs.enabled=true",
        "springdoc.swagger-ui.enabled=true"
})
@AutoConfigureMockMvc
@Testcontainers(disabledWithoutDocker = true)
class EmailVerificationMailpitFlowIntegrationTest {

    private static final DockerImageName MYSQL_IMAGE = DockerImageName.parse("mysql:8.4.5");
    private static final DockerImageName MAILPIT_IMAGE = DockerImageName.parse("axllent/mailpit:v1.30.7");
    private static final Pattern CODE_IN_MAIL = Pattern.compile("(?<![0-9])([0-9]{6})(?![0-9])");
    private static final String EMAIL = "Vertical.Flow+tag@Example.COM";
    private static final Instant NOW = Instant.parse("2026-08-21T00:00:00Z");

    @Container
    @ServiceConnection
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>(MYSQL_IMAGE);

    @Container
    static final GenericContainer<?> MAILPIT = new GenericContainer<>(MAILPIT_IMAGE)
            .withExposedPorts(1025, 8025);

    @Autowired
    MockMvc mockMvc;

    @Autowired
    ObjectMapper objectMapper;

    @Autowired
    EmailVerificationChallengeRepository challengeRepository;

    @Autowired
    EmailVerificationProofRepository proofRepository;

    @Autowired
    UserAccountRepository userRepository;

    @MockitoBean
    Clock clock;

    /**
     * Testcontainers Mailpit의 동적 SMTP 주소를 Spring Mail 구성에 전달합니다.
     *
     * @param registry 테스트 애플리케이션 속성 등록기
     */
    @DynamicPropertySource
    static void mailProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.mail.host", MAILPIT::getHost);
        registry.add("spring.mail.port", () -> MAILPIT.getMappedPort(1025));
    }

    /**
     * 격리된 데이터베이스를 비우고 모든 발급·가입 시각을 고정합니다.
     */
    @BeforeEach
    void setUp() {
        proofRepository.deleteAllInBatch();
        challengeRepository.deleteAllInBatch();
        userRepository.deleteAllInBatch();
        reset(clock);
        when(clock.instant()).thenReturn(NOW);
        when(clock.getZone()).thenReturn(ZoneOffset.UTC);
    }

    /**
     * 발송 API, Mailpit 본문, 확인 API, proof 전달과 기존 signup 소비가 끝까지 작동하는지 확인합니다.
     *
     * @throws Exception HTTP, JSON, 메일 조회 또는 MockMvc 처리에 실패한 경우
     */
    @Test
    void sendsMailConfirmsCodeAndConsumesProofDuringSignup() throws Exception {
        mockMvc.perform(get("/swagger-ui/index.html"))
                .andExpect(status().isOk());
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paths['/api/v1/auth/email-verifications'].post").exists())
                .andExpect(jsonPath("$.paths['/api/v1/auth/email-verifications/confirm'].post").exists())
                .andExpect(jsonPath("$.paths['/api/v1/auth/signup'].post").exists());

        mockMvc.perform(post("/api/v1/auth/email-verifications")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsBytes(sendRequest())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("EMAIL_VERIFICATION_CODE_SENT"));

        String verificationCode = extractVerificationCode(latestMailText());
        String confirmationBody = mockMvc.perform(post("/api/v1/auth/email-verifications/confirm")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsBytes(confirmRequest(verificationCode))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("EMAIL_VERIFICATION_CONFIRMED"))
                .andReturn().getResponse().getContentAsString();
        String verificationProof = objectMapper.readTree(confirmationBody)
                .path("data").path("verificationProof").asText();

        mockMvc.perform(post("/api/v1/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsBytes(signupRequest(verificationProof))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.code").value("SIGNUP_COMPLETED"))
                .andExpect(jsonPath("$.data.email").value(EMAIL));

        assertThat(userRepository.count()).isEqualTo(1);
        assertThat(challengeRepository.count()).isZero();
        assertThat(proofRepository.count()).isZero();
    }

    /**
     * 인증번호 발송 요청 JSON 객체를 생성합니다.
     *
     * @return 테스트 이메일을 가진 요청 객체
     */
    private ObjectNode sendRequest() {
        return objectMapper.createObjectNode().put("email", EMAIL);
    }

    /**
     * Mailpit에서 읽은 인증번호를 포함한 확인 요청 JSON 객체를 생성합니다.
     *
     * @param verificationCode 메일 본문에서 추출한 현재 인증번호
     * @return 이메일과 인증번호를 가진 요청 객체
     */
    private ObjectNode confirmRequest(String verificationCode) {
        return objectMapper.createObjectNode()
                .put("email", EMAIL)
                .put("verificationCode", verificationCode);
    }

    /**
     * 확인 API가 발급한 proof를 기존 signup 계약에 넣은 요청 객체를 생성합니다.
     *
     * @param verificationProof 확인 API가 반환한 일회성 증명
     * @return 필수 가입 필드와 동의 세 항목을 가진 요청 객체
     */
    private ObjectNode signupRequest(String verificationProof) {
        ObjectNode request = objectMapper.createObjectNode();
        request.put("verificationProof", verificationProof);
        request.put("password", generatedPassword());
        request.put("birthDate", "2000-01-01");
        ArrayNode consents = request.putArray("consents");
        consents.add(consent("TERMS"));
        consents.add(consent("PERSONAL_INFORMATION"));
        consents.add(consent("HEALTH_INFORMATION"));
        return request;
    }

    /**
     * 현재 정책 버전의 필수 동의 JSON 객체를 생성합니다.
     *
     * @param type 공개 동의 종류
     * @return 동의 종류와 현재 버전을 가진 객체
     */
    private JsonNode consent(String type) {
        return objectMapper.createObjectNode().put("type", type).put("version", "1.0");
    }

    /**
     * 소스에 고정 평문을 두지 않고 테스트 실행 중 비밀번호 정책을 충족하는 값을 생성합니다.
     *
     * @return 8자 이상 72바이트 이하의 테스트 비밀번호
     */
    private String generatedPassword() {
        byte[] random = new byte[18];
        new SecureRandom().nextBytes(random);
        return "T-" + java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(random);
    }

    /**
     * Mailpit 공식 최신 텍스트 보기 경로에서 방금 저장된 메일 본문을 조회합니다.
     *
     * @return 가장 최근 메일의 텍스트 본문
     * @throws Exception Mailpit HTTP 요청 또는 대기에 실패한 경우
     */
    private String latestMailText() throws Exception {
        HttpClient client = HttpClient.newHttpClient();
        URI uri = URI.create("http://" + MAILPIT.getHost() + ":" + MAILPIT.getMappedPort(8025)
                + "/view/latest.txt");
        for (int attempt = 0; attempt < 20; attempt++) {
            HttpResponse<String> response = client.send(
                    HttpRequest.newBuilder(uri).GET().build(),
                    HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 200 && !response.body().isBlank()) {
                return response.body();
            }
            Thread.sleep(100);
        }
        throw new IllegalStateException("Mailpit did not expose the delivered message in time");
    }

    /**
     * 메일 본문에서 공개하지 않을 현재 6자리 인증번호를 추출합니다.
     *
     * @param mailText Mailpit에서 조회한 텍스트 본문
     * @return 확인 API에 즉시 제출할 인증번호
     */
    private String extractVerificationCode(String mailText) {
        Matcher matcher = CODE_IN_MAIL.matcher(mailText);
        if (!matcher.find()) {
            throw new IllegalStateException("Mailpit message does not contain the expected code field");
        }
        return matcher.group(1);
    }
}

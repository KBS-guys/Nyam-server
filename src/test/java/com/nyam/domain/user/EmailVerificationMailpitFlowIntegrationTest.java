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

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.nyam.domain.user.repository.EmailVerificationChallengeRepository;
import com.nyam.domain.user.repository.UserAccountRepository;

/**
 * 실제 MySQL·Mailpit과 HTTP API를 연결해 발송에서 직접 signup까지 검증합니다.
 */
@SpringBootTest(properties = {
        "NYAM_EMAIL_VERIFICATION_HMAC_SECRET=QUFBQUFBQUFBQUFBQUFBQUFBQUFBQUFBQUFBQUFBQUE=",
        "NYAM_AUTH_ACCESS_SECRET=QUFBQUFBQUFBQUFBQUFBQUFBQUFBQUFBQUFBQUFBQUE=",
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
    private static final Instant NOW = Instant.parse("2026-08-27T00:00:00Z");

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
    UserAccountRepository userRepository;

    @MockitoBean
    Clock clock;

    @DynamicPropertySource
    static void mailProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.mail.host", MAILPIT::getHost);
        registry.add("spring.mail.port", () -> MAILPIT.getMappedPort(1025));
    }

    @BeforeEach
    void setUp() {
        challengeRepository.deleteAllInBatch();
        userRepository.deleteAllInBatch();
        reset(clock);
        when(clock.instant()).thenReturn(NOW);
        when(clock.getZone()).thenReturn(ZoneOffset.UTC);
    }

    @Test
    void sendsMailAndSignsUpWithTheCodeDirectly() throws Exception {
        mockMvc.perform(get("/swagger-ui/index.html"))
                .andExpect(status().isOk());
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paths['/api/v1/auth/email-verifications'].post").exists())
                .andExpect(jsonPath("$.paths['/api/v1/auth/email-verifications/confirm']").doesNotExist())
                .andExpect(jsonPath("$.paths['/api/v1/auth/signup'].post").exists());

        mockMvc.perform(post("/api/v1/auth/email-verifications")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsBytes(sendRequest())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("EMAIL_VERIFICATION_CODE_SENT"));

        String verificationCode = extractVerificationCode(latestMailText());
        mockMvc.perform(post("/api/v1/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsBytes(signupRequest(verificationCode))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.code").value("SIGNUP_COMPLETED"))
                .andExpect(jsonPath("$.data.email").value(EMAIL));

        assertThat(userRepository.count()).isEqualTo(1);
        assertThat(challengeRepository.count()).isZero();
    }

    private ObjectNode sendRequest() {
        return objectMapper.createObjectNode().put("email", EMAIL);
    }

    private ObjectNode signupRequest(String verificationCode) {
        ObjectNode request = objectMapper.createObjectNode();
        request.put("email", EMAIL.toLowerCase(java.util.Locale.ROOT));
        request.put("verificationCode", verificationCode);
        request.put("password", generatedPassword());
        request.put("birthDate", "2000-01-01");
        request.put("termsAgreed", true);
        request.put("personalInformationAgreed", true);
        request.put("healthInformationAgreed", true);
        return request;
    }

    private String generatedPassword() {
        byte[] random = new byte[18];
        new SecureRandom().nextBytes(random);
        return "T-" + java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(random);
    }

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

    private String extractVerificationCode(String mailText) {
        Matcher matcher = CODE_IN_MAIL.matcher(mailText);
        if (!matcher.find()) {
            throw new IllegalStateException("Mailpit message does not contain the expected code field");
        }
        return matcher.group(1);
    }
}

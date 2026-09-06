package com.nyam.deployment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.head;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.Base64;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.MockMvcPrint;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nyam.NyamApplication;
import com.nyam.domain.user.service.AccessTokenIssuer;
import com.nyam.domain.user.service.VerificationMailSender;

/**
 * 실제 MySQL과 보안 필터로 공개 문서·ping-only health와 보호 API 경계를 검증합니다.
 * 이 테스트의 로컬 DB 연결만 TLS를 끄며 실제 image의 TLS는 별도 container smoke로 검증합니다.
 */
@SpringBootTest(classes = NyamApplication.class, properties = {
        "spring.config.import=",
        "spring.datasource.hikari.data-source-properties.sslMode=DISABLED",
        "NYAM_OPENAPI_ENABLED=true"
})
@ActiveProfiles("deployment")
@AutoConfigureMockMvc(print = MockMvcPrint.NONE)
@Import(DeploymentHttpIntegrationTest.UnavailableDependency.class)
@Testcontainers(disabledWithoutDocker = true)
@ExtendWith(OutputCaptureExtension.class)
class DeploymentHttpIntegrationTest {

    @Container
    @ServiceConnection
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.4.5");

    @Autowired
    MockMvc mvc;
    @Autowired
    ObjectMapper mapper;
    @Autowired
    AccessTokenIssuer tokenIssuer;
    @Autowired
    JdbcTemplate jdbcTemplate;

    @MockitoSpyBean
    VerificationMailSender verificationMailSender;

    @DynamicPropertySource
    static void isolatedCredentials(DynamicPropertyRegistry registry) {
        String key = Base64.getEncoder().encodeToString(
                java.security.SecureRandom.getSeed(32));
        registry.add("NYAM_AUTH_ACCESS_SECRET", () -> key);
        registry.add("NYAM_EMAIL_VERIFICATION_HMAC_SECRET", () -> key);
        registry.add("MYSQL_URL", MYSQL::getJdbcUrl);
        registry.add("MYSQL_USERNAME", MYSQL::getUsername);
        registry.add("MYSQL_PASSWORD", MYSQL::getPassword);
        registry.add("MYSQL_TRUSTSTORE_URL", () -> "file:/not-used-in-this-local-test.p12");
        registry.add("MYSQL_TRUSTSTORE_PASSWORD", () -> UUID.randomUUID().toString());
    }

    @Test
    void renderHealthIsStatusOnlyHttp200DespiteUnrelatedDependencyFailure() throws Exception {
        mvc.perform(get("/actuator/health/render"))
                .andExpect(status().isOk())
                .andExpect(header().doesNotExist("Location"))
                .andExpect(content().string("{\"status\":\"UP\"}"));
    }

    @Test
    void doesNotLogTheJdbcEndpoint(CapturedOutput output) {
        String endpoint = MYSQL.getJdbcUrl().split("\\?", 2)[0];
        assertThat(output.getAll().contains(endpoint)).isFalse();
    }

    @Test
    void deniesEveryOtherActuatorPathAndNonGetHealthEvenWhenAuthenticated() throws Exception {
        String bearer = "Bearer " + tokenIssuer.issue(1L, Instant.now());
        for (String path : new String[] { "/actuator", "/actuator/health", "/actuator/env",
                "/actuator/health/render/ping", "/actuator/health/render/", "/actuator/health/liveness" }) {
            mvc.perform(get(path)).andExpect(status().isUnauthorized());
            mvc.perform(get(path).header("Authorization", bearer)).andExpect(status().isForbidden());
        }
        mvc.perform(post("/actuator/health/render")).andExpect(status().isUnauthorized());
        mvc.perform(head("/actuator/health/render")).andExpect(status().isUnauthorized());
        mvc.perform(post("/actuator/health/render").header("Authorization", bearer))
                .andExpect(status().isForbidden());
    }

    @Test
    void anonymouslyServesDocsWithSameOriginTryItOutWithoutGrantingApiAccess() throws Exception {
        mvc.perform(get("/swagger-ui.html")).andExpect(status().is3xxRedirection());
        mvc.perform(get("/swagger-ui/index.html")).andExpect(status().isOk());
        var result = mvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.servers[0].url").value("/"))
                .andExpect(jsonPath("$.components.securitySchemes.bearerAuth.type").value("http"))
                .andReturn();
        var paths = mapper.readTree(result.getResponse().getContentAsByteArray()).path("paths");
        assertThat(paths.size()).isPositive();
        paths.fieldNames().forEachRemaining(path -> assertThat(path).startsWith("/api/v1/"));
        mvc.perform(get("/v3/api-docs/swagger-config"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.persistAuthorization").value(false));
        mvc.perform(get("/api/v1/auth/me")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/foods/search").param("query", "food"))
                .andExpect(status().isUnauthorized());
    }

    /** deployment에서는 모든 auth 하위 경로를 인증 상태와 무관하게 차단하고 부작용을 만들지 않습니다. */
    @Test
    void blocksEveryAuthRouteWithoutMailOrDatabaseSideEffects() throws Exception {
        long usersBefore = count("users");
        long challengesBefore = count("email_verification_challenges");
        long refreshBefore = count("refresh_tokens");
        String bearer = "Bearer " + tokenIssuer.issue(7L, Instant.now());

        for (String path : new String[] {
                "/api/v1/auth/signup",
                "/api/v1/auth/login",
                "/api/v1/auth/refresh",
                "/api/v1/auth/logout",
                "/api/v1/auth/email-verifications",
                "/api/v1/auth/unmapped"
        }) {
            mvc.perform(post(path)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{}"))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.code").value("E003"))
                    .andExpect(header().doesNotExist(HttpHeaders.LOCATION))
                    .andExpect(header().doesNotExist(HttpHeaders.SET_COOKIE));
            mvc.perform(post(path)
                            .header(HttpHeaders.AUTHORIZATION, bearer)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{}"))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value("E004"))
                    .andExpect(header().doesNotExist(HttpHeaders.LOCATION))
                    .andExpect(header().doesNotExist(HttpHeaders.SET_COOKIE));
        }

        mvc.perform(get("/api/v1/auth/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("E003"));
        mvc.perform(get("/api/v1/auth/me").header(HttpHeaders.AUTHORIZATION, bearer))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("E004"));

        assertThat(count("users")).isEqualTo(usersBefore);
        assertThat(count("email_verification_challenges")).isEqualTo(challengesBefore);
        assertThat(count("refresh_tokens")).isEqualTo(refreshBefore);
        verifyNoInteractions(verificationMailSender);
    }

    /** 보호 API는 누락·변조·만료된 JWT를 동일한 401 계약으로 거절합니다. */
    @Test
    void rejectsMissingTamperedAndExpiredJwtOnProtectedApi() throws Exception {
        String valid = tokenIssuer.issue(7L, Instant.now());
        String expired = tokenIssuer.issue(7L, Instant.now().minusSeconds(901));
        String tampered = valid.substring(0, valid.length() - 1)
                + (valid.endsWith("a") ? "b" : "a");

        for (String authorization : new String[] { null, "Bearer " + tampered, "Bearer " + expired }) {
            var request = get("/api/v1/foods/search").param("query", "food");
            if (authorization != null) {
                request.header(HttpHeaders.AUTHORIZATION, authorization);
            }
            mvc.perform(request)
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.code").value("E003"));
        }
    }

    private long count(String table) {
        return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM " + table, Long.class);
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class UnavailableDependency {
        @Bean
        HealthIndicator unavailableRemote() {
            return () -> Health.down().withDetail("internal", "not-public").build();
        }
    }
}

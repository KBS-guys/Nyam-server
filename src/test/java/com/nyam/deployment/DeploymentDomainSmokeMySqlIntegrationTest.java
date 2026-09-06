package com.nyam.deployment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.MockMvcPrint;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nyam.deployment.smoke.SmokeSeedManifest;
import com.nyam.deployment.smoke.SmokeUserSeeder;
import com.nyam.domain.user.service.AccessTokenIssuer;

/**
 * deployment 보안 필터와 실제 MySQL 8.4.5를 통과하는 A/B 전체 도메인·소유권 smoke를 검증합니다.
 */
@SpringBootTest(properties = {
        "spring.config.import=",
        "spring.datasource.hikari.data-source-properties.sslMode=DISABLED",
        "NYAM_OPENAPI_ENABLED=false"
})
@ActiveProfiles("deployment")
@AutoConfigureMockMvc(print = MockMvcPrint.NONE)
@Testcontainers(disabledWithoutDocker = true)
class DeploymentDomainSmokeMySqlIntegrationTest {

    private static final long FOOD_ID = 901L;
    private static final String DATE = "2026-09-03";

    @Container
    @ServiceConnection
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.4.5");

    @Autowired
    MockMvc mvc;

    @Autowired
    ObjectMapper mapper;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @Autowired
    AccessTokenIssuer tokenIssuer;

    @DynamicPropertySource
    static void isolatedCredentials(DynamicPropertyRegistry registry) {
        String key = Base64.getEncoder().encodeToString(java.security.SecureRandom.getSeed(32));
        registry.add("NYAM_AUTH_ACCESS_SECRET", () -> key);
        registry.add("NYAM_EMAIL_VERIFICATION_HMAC_SECRET", () -> key);
        registry.add("MYSQL_URL", MYSQL::getJdbcUrl);
        registry.add("MYSQL_USERNAME", MYSQL::getUsername);
        registry.add("MYSQL_PASSWORD", MYSQL::getPassword);
        registry.add("MYSQL_TRUSTSTORE_URL", () -> "file:/not-used-in-this-local-test.p12");
        registry.add("MYSQL_TRUSTSTORE_PASSWORD", () -> UUID.randomUUID().toString());
    }

    @BeforeEach
    void resetDomainRows() {
        jdbcTemplate.update("DELETE FROM meal_items");
        jdbcTemplate.update("DELETE FROM meals");
        jdbcTemplate.update("DELETE FROM foods");
        jdbcTemplate.update("DELETE FROM refresh_tokens");
        jdbcTemplate.update("DELETE FROM user_consents");
        jdbcTemplate.update("DELETE FROM local_credentials");
        jdbcTemplate.update("DELETE FROM email_verification_challenges");
        jdbcTemplate.update("DELETE FROM users");
    }

    /** A의 food→meal→summary 흐름과 B의 비노출·삭제 은닉, 삭제 후 empty summary를 검증합니다. */
    @Test
    void executesTheAuthenticatedDomainFlowAndIsolatesOwners() throws Exception {
        SmokeSeedManifest.Users users;
        try (var connection = jdbcTemplate.getDataSource().getConnection()) {
            users = new SmokeUserSeeder().seed(connection, Instant.parse("2026-09-03T00:00:00Z"));
        }
        insertFood();
        String userA = bearer(users.userAId());
        String userB = bearer(users.userBId());

        mvc.perform(get("/api/v1/foods/search")
                        .queryParam("query", "현미")
                        .header(HttpHeaders.AUTHORIZATION, userA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].foodId").value(FOOD_ID));
        mvc.perform(get("/api/v1/foods/{foodId}", FOOD_ID)
                        .header(HttpHeaders.AUTHORIZATION, userA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.energy.value").value(120));

        var created = mvc.perform(post("/api/v1/meals")
                        .header(HttpHeaders.AUTHORIZATION, userA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"mealDate":"2026-09-03","items":[{"foodId":901,"amount":150.0000}]}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.userId").doesNotExist())
                .andReturn();
        long mealId = mapper.readTree(created.getResponse().getContentAsByteArray())
                .path("data").path("mealId").asLong();
        assertThat(mealId).isPositive();

        mvc.perform(get("/api/v1/meals")
                        .queryParam("date", DATE)
                        .header(HttpHeaders.AUTHORIZATION, userA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].mealId").value(mealId));
        mvc.perform(get("/api/v1/daily-summaries")
                        .queryParam("date", DATE)
                        .header(HttpHeaders.AUTHORIZATION, userA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.mealItemCount").value(1))
                .andExpect(jsonPath("$.data.energy.value").value(180))
                .andExpect(jsonPath("$.data.energy.complete").value(true));

        mvc.perform(get("/api/v1/meals")
                        .queryParam("date", DATE)
                        .header(HttpHeaders.AUTHORIZATION, userB))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isEmpty());
        mvc.perform(get("/api/v1/daily-summaries")
                        .queryParam("date", DATE)
                        .header(HttpHeaders.AUTHORIZATION, userB))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.mealItemCount").value(0));
        mvc.perform(delete("/api/v1/meals/{mealId}", mealId)
                        .header(HttpHeaders.AUTHORIZATION, userB))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("MEAL_NOT_FOUND"));
        mvc.perform(get("/api/v1/meals")
                        .queryParam("date", DATE)
                        .header(HttpHeaders.AUTHORIZATION, userA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].mealId").value(mealId));

        mvc.perform(delete("/api/v1/meals/{mealId}", mealId)
                        .header(HttpHeaders.AUTHORIZATION, userA))
                .andExpect(status().isOk());
        mvc.perform(get("/api/v1/daily-summaries")
                        .queryParam("date", DATE)
                        .header(HttpHeaders.AUTHORIZATION, userA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.mealItemCount").value(0))
                .andExpect(jsonPath("$.data.energy.value").value(0))
                .andExpect(jsonPath("$.data.energy.complete").value(true));
    }

    private String bearer(long userId) {
        return "Bearer " + tokenIssuer.issue(userId, Instant.now());
    }

    private void insertFood() {
        LocalDateTime timestamp = LocalDateTime.of(2026, 9, 3, 0, 0);
        jdbcTemplate.update("""
                INSERT INTO foods (
                    food_id, source_food_code, food_name, normalized_name, food_type,
                    basis_amount, basis_unit, energy, energy_unit,
                    carbohydrate, carbohydrate_unit, protein, protein_unit, fat, fat_unit,
                    created_at, updated_at)
                VALUES (?, 'P001-000000901-0001', '현미밥', '현미밥', 'P',
                        100.0000, 'G', 120.0000, 'KCAL',
                        25.0000, 'G', 3.0000, 'G', 1.0000, 'G', ?, ?)
                """, FOOD_ID, timestamp, timestamp);
    }
}

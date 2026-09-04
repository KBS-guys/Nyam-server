package com.nyam.deployment.smoke;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.time.Instant;

import javax.sql.DataSource;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * 실제 MySQL 8.4.5에서 manifest 실패와 합성 사용자 transaction의 원자성을 검증합니다.
 */
@SpringBootTest(properties = {
        "NYAM_EMAIL_VERIFICATION_HMAC_SECRET=QUFBQUFBQUFBQUFBQUFBQUFBQUFBQUFBQUFBQUFBQUE=",
        "NYAM_AUTH_ACCESS_SECRET=QUFBQUFBQUFBQUFBQUFBQUFBQUFBQUFBQUFBQUFBQUE=",
        "springdoc.api-docs.enabled=false",
        "springdoc.swagger-ui.enabled=false"
})
@Testcontainers(disabledWithoutDocker = true)
class SmokeUserSeedApplicationMySqlIntegrationTest {

    @Container
    @ServiceConnection
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.4.5");

    @Autowired
    DataSource dataSource;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @TempDir
    Path tempDir;

    @BeforeEach
    void resetUsers() {
        jdbcTemplate.update("DELETE FROM meal_items");
        jdbcTemplate.update("DELETE FROM meals");
        jdbcTemplate.update("DELETE FROM refresh_tokens");
        jdbcTemplate.update("DELETE FROM user_consents");
        jdbcTemplate.update("DELETE FROM local_credentials");
        jdbcTemplate.update("DELETE FROM email_verification_challenges");
        jdbcTemplate.update("DELETE FROM users");
    }

    /** manifest 파일을 만들지 못하면 명령이 실패하고 신규 A/B도 rollback됩니다. */
    @Test
    void rollsBackNewUsersWhenManifestWriteFails() throws Exception {
        Path manifest = tempDir.resolve("smoke-seed.properties");
        int status;
        try (Connection connection = dataSource.getConnection()) {
            status = SmokeUserSeedApplication.seedAndWriteManifest(
                    connection,
                    Instant.parse("2026-09-04T00:00:00Z"),
                    manifest,
                    "rollback-test",
                    (output, target, users) -> {
                        Files.writeString(output, "partial");
                        throw new IOException("simulated manifest failure");
                    });
        }

        assertThat(status).isEqualTo(1);
        assertThat(manifest).doesNotExist();
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM users", Long.class)).isZero();
    }
}

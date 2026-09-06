package com.nyam.deployment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.Connection;
import java.time.Instant;
import java.time.LocalDateTime;

import javax.sql.DataSource;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import com.nyam.deployment.smoke.SmokeSeedManifest;
import com.nyam.deployment.smoke.SmokeUserSeeder;

/**
 * 실제 MySQL 8.4.5에서 합성 사용자 A/B seed의 생성·재사용·충돌 rollback을 검증합니다.
 */
@SpringBootTest(properties = {
        "NYAM_EMAIL_VERIFICATION_HMAC_SECRET=QUFBQUFBQUFBQUFBQUFBQUFBQUFBQUFBQUFBQUFBQUE=",
        "NYAM_AUTH_ACCESS_SECRET=QUFBQUFBQUFBQUFBQUFBQUFBQUFBQUFBQUFBQUFBQUE=",
        "springdoc.api-docs.enabled=false",
        "springdoc.swagger-ui.enabled=false"
})
@Testcontainers(disabledWithoutDocker = true)
class SmokeUserSeederMySqlIntegrationTest {

    private static final Instant NOW = Instant.parse("2026-09-03T00:00:00Z");

    @Container
    @ServiceConnection
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.4.5");

    @Autowired
    DataSource dataSource;

    @Autowired
    JdbcTemplate jdbcTemplate;

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

    /** 첫 실행은 users 두 행만 MySQL 생성 ID로 만들고 인증 관련 행을 만들지 않습니다. */
    @Test
    void createsOnlyTwoSyntheticUsersWithGeneratedIds() throws Exception {
        SmokeSeedManifest.Users users = seed();

        assertThat(users.userAId()).isPositive().isNotEqualTo(users.userBId());
        assertThat(users.userBId()).isPositive();
        assertThat(jdbcTemplate.queryForObject("SELECT VERSION()", String.class)).startsWith("8.4.5");
        assertThat(count("users")).isEqualTo(2);
        assertThat(jdbcTemplate.queryForList("""
                SELECT display_email, canonical_email, birth_date, created_at
                FROM users
                ORDER BY canonical_email
                """))
                .allSatisfy(row -> {
                    assertThat(row.get("display_email")).isEqualTo(row.get("canonical_email"));
                    assertThat(row.get("created_at")).isNotNull();
                });
        assertThat(count("local_credentials")).isZero();
        assertThat(count("user_consents")).isZero();
        assertThat(count("refresh_tokens")).isZero();
        assertThat(count("email_verification_challenges")).isZero();
    }

    /** 같은 대상에 다시 실행하면 기존 fixture를 엄격히 검증하고 같은 ID를 재사용합니다. */
    @Test
    void reusesTheExactFixtureWithoutDuplicates() throws Exception {
        SmokeSeedManifest.Users first = seed();
        SmokeSeedManifest.Users second = seed();

        assertThat(second).isEqualTo(first);
        assertThat(count("users")).isEqualTo(2);
    }

    /** B 충돌을 A 생성 뒤 발견해도 A 생성을 rollback하고 기존 B를 수정하지 않습니다. */
    @Test
    void rollsBackPartialCreationWhenAnExistingFixtureConflicts() {
        jdbcTemplate.update("""
                INSERT INTO users (display_email, canonical_email, birth_date, created_at)
                VALUES (?, ?, '2000-01-02', ?)
                """, "different@example.invalid", SmokeSeedManifest.USER_B_EMAIL,
                LocalDateTime.of(2026, 9, 3, 0, 0));

        assertThatThrownBy(this::seed)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("conflicts");

        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM users WHERE canonical_email = ?", Long.class,
                SmokeSeedManifest.USER_A_EMAIL)).isZero();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT display_email FROM users WHERE canonical_email = ?", String.class,
                SmokeSeedManifest.USER_B_EMAIL)).isEqualTo("different@example.invalid");
    }

    /** 기존 fixture에 credential이 붙어 있으면 일반 계정으로 간주하고 재사용하지 않습니다. */
    @Test
    void rejectsASeedUserThatHasAuthenticationData() throws Exception {
        SmokeSeedManifest.Users users = seed();
        jdbcTemplate.update("""
                INSERT INTO local_credentials (user_id, password_hash, created_at)
                VALUES (?, 'not-a-real-credential', ?)
                """, users.userAId(), LocalDateTime.of(2026, 9, 3, 0, 1));

        assertThatThrownBy(this::seed)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("authentication data");
        assertThat(count("users")).isEqualTo(2);
        assertThat(count("local_credentials")).isOne();
    }

    private SmokeSeedManifest.Users seed() throws Exception {
        try (Connection connection = dataSource.getConnection()) {
            return new SmokeUserSeeder().seed(connection, NOW);
        }
    }

    private long count(String table) {
        return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM " + table, Long.class);
    }
}

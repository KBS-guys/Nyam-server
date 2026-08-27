package com.nyam.domain.user;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Timestamp;
import java.time.LocalDateTime;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

/**
 * 기존 V3 설치가 V4 proof 제거 스키마로 안전하게 전진하는지 검증합니다.
 */
@Testcontainers(disabledWithoutDocker = true)
class AuthScopeReductionMigrationIntegrationTest {

    private static final DockerImageName MYSQL_IMAGE = DockerImageName.parse("mysql:8.4.5");

    @Container
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>(MYSQL_IMAGE);

    @Test
    void upgradesV3SchemaByDroppingProofAndPreservingChallenge() throws Exception {
        Flyway.configure()
                .dataSource(MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword())
                .locations("classpath:db/migration")
                .target("3")
                .load()
                .migrate();

        insertV3AuthenticationState();

        Flyway.configure()
                .dataSource(MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword())
                .locations("classpath:db/migration")
                .load()
                .migrate();

        try (Connection connection = MYSQL.createConnection("")) {
            assertThat(tableExists(connection, "email_verification_proofs")).isFalse();
            assertThat(tableExists(connection, "email_verification_challenges")).isTrue();
            assertThat(rowCount(connection, "email_verification_challenges")).isEqualTo(1);
            assertThat(appliedVersion(connection)).isEqualTo("4");
        }
    }

    private void insertV3AuthenticationState() throws Exception {
        LocalDateTime now = LocalDateTime.of(2026, 8, 27, 0, 0);
        try (Connection connection = MYSQL.createConnection("");
                PreparedStatement proof = connection.prepareStatement("""
                        INSERT INTO email_verification_proofs
                            (proof_hash, display_email, canonical_email, created_at, expires_at)
                        VALUES (?, ?, ?, ?, ?)
                        """);
                PreparedStatement challenge = connection.prepareStatement("""
                        INSERT INTO email_verification_challenges
                            (canonical_email, display_email, code_verifier, verification_started_at,
                             code_issued_at, expires_at, resend_count, failed_attempt_count)
                        VALUES (?, ?, ?, ?, ?, ?, 0, 0)
                        """)) {
            proof.setBytes(1, new byte[32]);
            proof.setString(2, "Legacy@Example.COM");
            proof.setString(3, "legacy@example.com");
            proof.setTimestamp(4, Timestamp.valueOf(now));
            proof.setTimestamp(5, Timestamp.valueOf(now.plusMinutes(15)));
            proof.executeUpdate();

            challenge.setString(1, "challenge@example.com");
            challenge.setString(2, "Challenge@Example.COM");
            challenge.setBytes(3, new byte[32]);
            challenge.setTimestamp(4, Timestamp.valueOf(now));
            challenge.setTimestamp(5, Timestamp.valueOf(now));
            challenge.setTimestamp(6, Timestamp.valueOf(now.plusMinutes(5)));
            challenge.executeUpdate();
        }
    }

    private boolean tableExists(Connection connection, String tableName) throws Exception {
        try (ResultSet tables = connection.getMetaData().getTables(
                connection.getCatalog(), null, tableName, new String[] {"TABLE"})) {
            return tables.next();
        }
    }

    private long rowCount(Connection connection, String tableName) throws Exception {
        try (var statement = connection.createStatement();
                ResultSet result = statement.executeQuery("SELECT COUNT(*) FROM " + tableName)) {
            result.next();
            return result.getLong(1);
        }
    }

    private String appliedVersion(Connection connection) throws Exception {
        try (var statement = connection.createStatement();
                ResultSet result = statement.executeQuery("""
                        SELECT version
                        FROM flyway_schema_history
                        WHERE success = TRUE AND version IS NOT NULL
                        ORDER BY installed_rank DESC
                        LIMIT 1
                        """)) {
            result.next();
            return result.getString(1);
        }
    }
}

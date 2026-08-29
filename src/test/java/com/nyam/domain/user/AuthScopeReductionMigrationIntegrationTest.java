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
 * 후속 Migration의 존재와 무관하게 기존 V3 설치가 V4 proof 제거 스키마로 안전하게 전진하는지 검증합니다.
 */
@Testcontainers(disabledWithoutDocker = true)
class AuthScopeReductionMigrationIntegrationTest {

    private static final DockerImageName MYSQL_IMAGE = DockerImageName.parse("mysql:8.4.5");

    @Container
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>(MYSQL_IMAGE);

    /**
     * V3 인증 데이터를 준비한 뒤 V4까지만 적용하여 proof 제거와 challenge 보존을 확인합니다.
     *
     * @throws Exception Flyway 실행 또는 실제 MySQL 검증에 실패한 경우
     */
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
                .target("4")
                .load()
                .migrate();

        try (Connection connection = MYSQL.createConnection("")) {
            assertThat(tableExists(connection, "email_verification_proofs")).isFalse();
            assertThat(tableExists(connection, "email_verification_challenges")).isTrue();
            assertThat(rowCount(connection, "email_verification_challenges")).isEqualTo(1);
            assertThat(appliedVersion(connection)).isEqualTo("4");
        }
    }

    /**
     * V4 적용 전 제거 대상 proof와 보존 대상 challenge 데이터를 V3 스키마에 삽입합니다.
     *
     * @throws Exception 실제 MySQL 연결 또는 데이터 삽입에 실패한 경우
     */
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

    /**
     * 현재 데이터베이스에 지정한 테이블이 존재하는지 확인합니다.
     *
     * @param connection 실제 MySQL 연결
     * @param tableName 확인할 테이블 이름
     * @return 테이블이 존재하면 {@code true}
     * @throws Exception 메타데이터 조회에 실패한 경우
     */
    private boolean tableExists(Connection connection, String tableName) throws Exception {
        try (ResultSet tables = connection.getMetaData().getTables(
                connection.getCatalog(), null, tableName, new String[] {"TABLE"})) {
            return tables.next();
        }
    }

    /**
     * 지정한 테이블의 현재 행 수를 조회합니다.
     *
     * @param connection 실제 MySQL 연결
     * @param tableName 행 수를 확인할 테이블 이름
     * @return 조회된 행 수
     * @throws Exception 쿼리 실행에 실패한 경우
     */
    private long rowCount(Connection connection, String tableName) throws Exception {
        try (var statement = connection.createStatement();
                ResultSet result = statement.executeQuery("SELECT COUNT(*) FROM " + tableName)) {
            result.next();
            return result.getLong(1);
        }
    }

    /**
     * 성공한 Flyway Migration 중 가장 최근 버전을 반환합니다.
     *
     * @param connection 실제 MySQL 연결
     * @return 가장 최근 성공 Migration 버전
     * @throws Exception 스키마 이력 조회에 실패한 경우
     */
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

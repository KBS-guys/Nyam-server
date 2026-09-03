package com.nyam.deployment.smoke;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

/**
 * 기존 schema에 합성 사용자 A/B만 한 트랜잭션으로 생성하거나 엄격히 재검증합니다.
 */
public final class SmokeUserSeeder {

    private static final LocalDate USER_A_BIRTH_DATE = LocalDate.of(2000, 1, 1);
    private static final LocalDate USER_B_BIRTH_DATE = LocalDate.of(2000, 1, 2);

    /**
     * MySQL 생성 ID를 사용하는 합성 사용자 A/B를 repeat-safe하게 준비합니다.
     * 기존 행의 필수 속성이나 인증 관련 데이터가 다르면 수정하지 않고 전체 작업을 취소합니다.
     *
     * @param connection migration이 완료된 전용 MySQL 연결
     * @param now 사용자 생성에 사용할 UTC 기준 시각
     * @return 검증된 A/B 사용자 ID
     * @throws SQLException 데이터베이스 작업이 실패한 경우
     */
    public SmokeSeedManifest.Users seed(Connection connection, Instant now) throws SQLException {
        boolean originalAutoCommit = connection.getAutoCommit();
        int originalIsolation = connection.getTransactionIsolation();
        connection.setAutoCommit(false);
        connection.setTransactionIsolation(Connection.TRANSACTION_READ_COMMITTED);
        try {
            long userA = findOrCreate(connection, SmokeSeedManifest.USER_A_EMAIL, USER_A_BIRTH_DATE, now);
            long userB = findOrCreate(connection, SmokeSeedManifest.USER_B_EMAIL, USER_B_BIRTH_DATE, now);
            requireNoAuthenticationData(connection, userA, SmokeSeedManifest.USER_A_EMAIL);
            requireNoAuthenticationData(connection, userB, SmokeSeedManifest.USER_B_EMAIL);
            SmokeSeedManifest.Users users = new SmokeSeedManifest.Users(userA, userB);
            connection.commit();
            return users;
        } catch (SQLException | RuntimeException exception) {
            connection.rollback();
            throw exception;
        } finally {
            connection.setTransactionIsolation(originalIsolation);
            connection.setAutoCommit(originalAutoCommit);
        }
    }

    private long findOrCreate(
            Connection connection,
            String canonicalEmail,
            LocalDate birthDate,
            Instant now) throws SQLException {
        try (PreparedStatement select = connection.prepareStatement("""
                SELECT user_id, display_email, canonical_email, birth_date, created_at
                FROM users
                WHERE canonical_email = ?
                FOR UPDATE
                """)) {
            select.setString(1, canonicalEmail);
            try (ResultSet rows = select.executeQuery()) {
                if (rows.next()) {
                    long id = rows.getLong("user_id");
                    boolean matches = id > 0
                            && canonicalEmail.equals(rows.getString("display_email"))
                            && canonicalEmail.equals(rows.getString("canonical_email"))
                            && birthDate.equals(rows.getObject("birth_date", LocalDate.class))
                            && rows.getTimestamp("created_at") != null;
                    if (!matches || rows.next()) {
                        throw new IllegalStateException("Smoke user fixture conflicts with existing data");
                    }
                    return id;
                }
            }
        }

        try (PreparedStatement insert = connection.prepareStatement("""
                INSERT INTO users (display_email, canonical_email, birth_date, created_at)
                VALUES (?, ?, ?, ?)
                """, Statement.RETURN_GENERATED_KEYS)) {
            insert.setString(1, canonicalEmail);
            insert.setString(2, canonicalEmail);
            insert.setObject(3, birthDate);
            LocalDateTime createdAt = LocalDateTime.ofInstant(now, ZoneOffset.UTC);
            insert.setTimestamp(4, Timestamp.valueOf(createdAt));
            if (insert.executeUpdate() != 1) {
                throw new IllegalStateException("Smoke user insert count is invalid");
            }
            try (ResultSet keys = insert.getGeneratedKeys()) {
                if (!keys.next()) {
                    throw new IllegalStateException("MySQL did not generate a smoke user ID");
                }
                long id = keys.getLong(1);
                if (id <= 0 || keys.next()) {
                    throw new IllegalStateException("Generated smoke user ID is invalid");
                }
                return id;
            }
        }
    }

    private void requireNoAuthenticationData(Connection connection, long userId, String email) throws SQLException {
        requireZero(connection, "SELECT COUNT(*) FROM local_credentials WHERE user_id = ?", userId);
        requireZero(connection, "SELECT COUNT(*) FROM user_consents WHERE user_id = ?", userId);
        requireZero(connection, "SELECT COUNT(*) FROM refresh_tokens WHERE user_id = ?", userId);
        requireZero(connection, "SELECT COUNT(*) FROM email_verification_challenges WHERE canonical_email = ?", email);
    }

    private void requireZero(Connection connection, String sql, Object value) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setObject(1, value);
            try (ResultSet rows = statement.executeQuery()) {
                if (!rows.next() || rows.getLong(1) != 0 || rows.next()) {
                    throw new IllegalStateException("Smoke user has unexpected authentication data");
                }
            }
        }
    }
}
